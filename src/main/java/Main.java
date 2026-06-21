import java.util.*;
import java.io.*;

public class Main {

    static Set<String> builtins = new HashSet<>(
            Arrays.asList("echo", "exit", "type", "pwd", "cd", "jobs"));

    // Keep track of background jobs
    static List<BackgroundJob> backgroundJobs = new ArrayList<>();

    static class BackgroundJob {
        int id;
        String command;
        Process process;
        boolean completed;
        String status;
        boolean reaped;

        BackgroundJob(int id, String command, Process process) {
            this.id = id;
            this.command = command;
            this.process = process;
            this.completed = false;
            this.status = "Running";
            this.reaped = false;
        }
    }

    // Get the next available job number
    static int getNextJobNumber() {
        if (backgroundJobs.isEmpty()) {
            return 1;
        }
        
        int highest = 0;
        for (BackgroundJob job : backgroundJobs) {
            if (job.id > highest) {
                highest = job.id;
            }
        }
        return highest + 1;
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        // Clean up background jobs on exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            for (BackgroundJob job : backgroundJobs) {
                if (job.process != null && job.process.isAlive()) {
                    job.process.destroy();
                }
            }
        }));

        while (true) {
            // Reap completed jobs before showing the prompt
            reapCompletedJobs();

            System.out.print("$ ");
            System.out.flush();

            String input = sc.nextLine();

            if (input.trim().equals("exit") || input.trim().equals("exit 0")) {
                // Clean up background jobs before exiting
                for (BackgroundJob job : backgroundJobs) {
                    if (job.process != null && job.process.isAlive()) {
                        job.process.destroy();
                    }
                }
                return;
            }

            if (input.trim().isEmpty())
                continue;

            // Parse the input into command and arguments with quote support
            List<String> tokens = parseInput(input);
            
            if (tokens.isEmpty())
                continue;
            
            // Check for pipeline
            int pipeIndex = -1;
            for (int i = 0; i < tokens.size(); i++) {
                if (tokens.get(i).equals("|")) {
                    pipeIndex = i;
                    break;
                }
            }
            
            // If there's a pipeline, handle it
            if (pipeIndex != -1) {
                // Split tokens into left and right commands
                List<String> leftTokens = tokens.subList(0, pipeIndex);
                List<String> rightTokens = tokens.subList(pipeIndex + 1, tokens.size());
                
                // Remove background job indicator from the right command if present
                boolean background = false;
                if (!rightTokens.isEmpty() && rightTokens.get(rightTokens.size() - 1).equals("&")) {
                    background = true;
                    rightTokens.remove(rightTokens.size() - 1);
                }
                
                // Execute the pipeline
                executePipeline(leftTokens, rightTokens, background);
                
                // Reap completed jobs after pipeline
                reapCompletedJobs();
                continue;
            }
            
            // Check for background job (&)
            boolean background = false;
            if (!tokens.isEmpty() && tokens.get(tokens.size() - 1).equals("&")) {
                background = true;
                tokens.remove(tokens.size() - 1);
            }
            
            // Check for output redirection
            int redirIndex = -1;
            String outputFile = null;
            boolean redirectStderr = false;
            boolean redirectStdout = false;
            boolean appendMode = false;
            
            for (int i = 0; i < tokens.size(); i++) {
                String token = tokens.get(i);
                if (token.equals(">") || token.equals("1>")) {
                    redirIndex = i;
                    redirectStdout = true;
                    redirectStderr = false;
                    appendMode = false;
                    if (i + 1 < tokens.size()) {
                        outputFile = tokens.get(i + 1);
                    }
                    break;
                } else if (token.equals(">>") || token.equals("1>>")) {
                    redirIndex = i;
                    redirectStdout = true;
                    redirectStderr = false;
                    appendMode = true;
                    if (i + 1 < tokens.size()) {
                        outputFile = tokens.get(i + 1);
                    }
                    break;
                } else if (token.equals("2>")) {
                    redirIndex = i;
                    redirectStdout = false;
                    redirectStderr = true;
                    appendMode = false;
                    if (i + 1 < tokens.size()) {
                        outputFile = tokens.get(i + 1);
                    }
                    break;
                } else if (token.equals("2>>")) {
                    redirIndex = i;
                    redirectStdout = false;
                    redirectStderr = true;
                    appendMode = true;
                    if (i + 1 < tokens.size()) {
                        outputFile = tokens.get(i + 1);
                    }
                    break;
                }
            }
            
            // Extract command and arguments
            List<String> cmdTokens;
            if (redirIndex != -1) {
                cmdTokens = tokens.subList(0, redirIndex);
            } else {
                cmdTokens = tokens;
            }
            
            if (cmdTokens.isEmpty())
                continue;
                
            String command = cmdTokens.get(0);
            String[] argsArr = cmdTokens.size() > 1 ? 
                cmdTokens.subList(1, cmdTokens.size()).toArray(new String[0]) : 
                new String[0];

            // Build the full command string with arguments for display
            StringBuilder fullCommandBuilder = new StringBuilder(command);
            for (String arg : argsArr) {
                fullCommandBuilder.append(" ").append(arg);
            }
            String fullCommandString = fullCommandBuilder.toString();

            boolean redirectOutput = (redirIndex != -1 && outputFile != null);

            // jobs builtin
            if (command.equals("jobs")) {
                // Check each job's status
                List<BackgroundJob> completedJobs = new ArrayList<>();
                List<BackgroundJob> runningJobs = new ArrayList<>();
                
                for (BackgroundJob job : backgroundJobs) {
                    if (job.process != null) {
                        try {
                            int exitCode = job.process.exitValue();
                            job.completed = true;
                            job.status = "Done";
                            completedJobs.add(job);
                        } catch (IllegalThreadStateException e) {
                            job.status = "Running";
                            runningJobs.add(job);
                        }
                    } else {
                        job.completed = true;
                        job.status = "Done";
                        completedJobs.add(job);
                    }
                }
                
                List<BackgroundJob> allJobs = new ArrayList<>();
                allJobs.addAll(runningJobs);
                allJobs.addAll(completedJobs);
                allJobs.sort((a, b) -> Integer.compare(a.id, b.id));
                
                if (!allJobs.isEmpty()) {
                    int size = allJobs.size();
                    for (int i = 0; i < size; i++) {
                        BackgroundJob job = allJobs.get(i);
                        
                        String marker;
                        if (i == size - 1) {
                            marker = "+";
                        } else if (i == size - 2) {
                            marker = "-";
                        } else {
                            marker = " ";
                        }
                        
                        String status = job.status;
                        String paddedStatus = String.format("%-24s", status);
                        
                        String cmdDisplay = job.command;
                        if (job.status.equals("Running")) {
                            cmdDisplay = job.command + " &";
                        }
                        
                        System.out.println("[" + job.id + "]" + marker + "  " + paddedStatus + cmdDisplay);
                    }
                }
                
                backgroundJobs = runningJobs;
                continue;
            }

            boolean isBuiltin = builtins.contains(command);
            
            if (isBuiltin || !background) {
                executeCommand(command, argsArr, redirectOutput, redirectStdout, 
                              redirectStderr, outputFile, appendMode, background, isBuiltin);
            } else {
                runBackgroundJob(command, argsArr, fullCommandString, redirectOutput, redirectStdout, 
                               redirectStderr, outputFile, appendMode);
            }
            
            reapCompletedJobs();
        }
    }

    static void executePipeline(List<String> leftTokens, List<String> rightTokens, boolean background) {
        try {
            // Parse left command
            if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
                System.out.println("Invalid pipeline");
                return;
            }
            
            String leftCommand = leftTokens.get(0);
            String[] leftArgs = leftTokens.size() > 1 ? 
                leftTokens.subList(1, leftTokens.size()).toArray(new String[0]) : 
                new String[0];
            
            String rightCommand = rightTokens.get(0);
            String[] rightArgs = rightTokens.size() > 1 ? 
                rightTokens.subList(1, rightTokens.size()).toArray(new String[0]) : 
                new String[0];
            
            // Find executables
            String leftPath = findExecutable(leftCommand);
            String rightPath = findExecutable(rightCommand);
            
            if (leftPath == null) {
                System.out.println(leftCommand + ": command not found");
                return;
            }
            if (rightPath == null) {
                System.out.println(rightCommand + ": command not found");
                return;
            }
            
            // Build ProcessBuilder for both commands
            List<String> leftCmd = new ArrayList<>();
            leftCmd.add(leftCommand);
            leftCmd.addAll(Arrays.asList(leftArgs));
            
            List<String> rightCmd = new ArrayList<>();
            rightCmd.add(rightCommand);
            rightCmd.addAll(Arrays.asList(rightArgs));
            
            ProcessBuilder leftPb = new ProcessBuilder(leftCmd);
            ProcessBuilder rightPb = new ProcessBuilder(rightCmd);
            
            // Important: Inherit stdout of right process so output goes to terminal
            rightPb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            
            Process leftProcess = leftPb.start();
            Process rightProcess = rightPb.start();
            
            // Pipe output from left to right's stdin
            InputStream leftOutput = leftProcess.getInputStream();
            OutputStream rightInput = rightProcess.getOutputStream();
            
            // Create a thread to pipe the data
            Thread pipeThread = new Thread(() -> {
                try {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = leftOutput.read(buffer)) != -1) {
                        rightInput.write(buffer, 0, bytesRead);
                        rightInput.flush();
                    }
                    rightInput.close();
                } catch (IOException e) {
                    // Ignore
                }
            });
            pipeThread.start();
            
            // Wait for left process to finish
            leftProcess.waitFor();
            
            // Wait for pipe thread to finish
            try {
                pipeThread.join();
            } catch (InterruptedException e) {
                // Ignore
            }
            
            // Close right input to signal EOF
            rightInput.close();
            
            // Wait for right process to finish
            rightProcess.waitFor();
            
        } catch (Exception e) {
            System.err.println("Error executing pipeline: " + e.getMessage());
        }
    }

    static void reapCompletedJobs() {
        List<BackgroundJob> completedJobs = new ArrayList<>();
        List<BackgroundJob> runningJobs = new ArrayList<>();
        
        for (BackgroundJob job : backgroundJobs) {
            if (job.process != null) {
                try {
                    int exitCode = job.process.exitValue();
                    job.completed = true;
                    job.status = "Done";
                    completedJobs.add(job);
                } catch (IllegalThreadStateException e) {
                    job.status = "Running";
                    runningJobs.add(job);
                }
            } else {
                job.completed = true;
                job.status = "Done";
                completedJobs.add(job);
            }
        }
        
        if (!completedJobs.isEmpty()) {
            completedJobs.sort((a, b) -> Integer.compare(a.id, b.id));
            
            for (BackgroundJob job : completedJobs) {
                String marker = " ";
                if (job.id == completedJobs.get(completedJobs.size() - 1).id) {
                    marker = "+";
                } else if (completedJobs.size() > 1 && 
                           job.id == completedJobs.get(completedJobs.size() - 2).id) {
                    marker = "-";
                }
                
                String status = "Done";
                String paddedStatus = String.format("%-24s", status);
                System.out.println("[" + job.id + "]" + marker + "  " + paddedStatus + job.command);
            }
        }
        
        backgroundJobs = runningJobs;
    }

    static void executeCommand(String command, String[] argsArr, 
                               boolean redirectOutput, boolean redirectStdout,
                               boolean redirectStderr, String outputFile, 
                               boolean appendMode, boolean background, boolean isBuiltin) {
        if (command.equals("echo")) {
            String output = String.join(" ", argsArr);
            if (redirectOutput && redirectStdout) {
                writeToFile(outputFile, output, appendMode);
            } else if (redirectOutput && redirectStderr) {
                createEmptyFile(outputFile);
                System.out.println(output);
            } else {
                System.out.println(output);
            }
            return;
        }

        if (command.equals("pwd")) {
            String cwd = System.getProperty("user.dir");
            if (redirectOutput && redirectStdout) {
                writeToFile(outputFile, cwd, appendMode);
            } else if (redirectOutput && redirectStderr) {
                createEmptyFile(outputFile);
                System.out.println(cwd);
            } else {
                System.out.println(cwd);
            }
            return;
        }

        if (command.equals("cd")) {
            if (argsArr.length == 0) {
                String home = System.getenv("HOME");
                if (home == null) {
                    home = System.getProperty("user.home");
                }
                System.setProperty("user.dir", home);
                if (redirectOutput && redirectStderr) {
                    createEmptyFile(outputFile);
                }
            } else {
                String newPath = argsArr[0];
                
                try {
                    String currentDir = System.getProperty("user.dir");
                    File newDir;
                    
                    if (newPath.startsWith("~")) {
                        String home = System.getenv("HOME");
                        if (home == null) {
                            home = System.getProperty("user.home");
                        }
                        
                        if (newPath.equals("~")) {
                            newDir = new File(home);
                        } else if (newPath.startsWith("~/")) {
                            newDir = new File(home + newPath.substring(1));
                        } else {
                            newDir = new File(currentDir, newPath);
                        }
                    } else if (newPath.startsWith("/")) {
                        newDir = new File(newPath);
                    } else {
                        newDir = new File(currentDir, newPath);
                    }
                    
                    String canonicalPath = newDir.getCanonicalPath();
                    File canonicalFile = new File(canonicalPath);
                    
                    if (canonicalFile.exists() && canonicalFile.isDirectory()) {
                        System.setProperty("user.dir", canonicalPath);
                        if (redirectOutput && redirectStderr) {
                            createEmptyFile(outputFile);
                        }
                    } else {
                        String errorMsg = "cd: " + newPath + ": No such file or directory";
                        if (redirectOutput && redirectStderr) {
                            writeToFile(outputFile, errorMsg, appendMode);
                        } else if (redirectOutput && redirectStdout) {
                            System.err.println(errorMsg);
                        } else {
                            System.out.println(errorMsg);
                        }
                    }
                    
                } catch (IOException e) {
                    String errorMsg = "cd: " + newPath + ": Error changing directory";
                    if (redirectOutput && redirectStderr) {
                        writeToFile(outputFile, errorMsg, appendMode);
                    } else if (redirectOutput && redirectStdout) {
                        System.err.println(errorMsg);
                    } else {
                        System.out.println(errorMsg);
                    }
                }
            }
            return;
        }

        if (command.equals("type")) {
            String cmd = argsArr.length > 0 ? argsArr[0] : "";
            String output = "";

            if (builtins.contains(cmd)) {
                output = cmd + " is a shell builtin";
            } else {
                String path = findExecutable(cmd);

                if (path != null) {
                    output = cmd + " is " + path;
                } else {
                    output = cmd + ": not found";
                }
            }
            
            if (redirectOutput && redirectStdout) {
                writeToFile(outputFile, output, appendMode);
            } else if (redirectOutput && redirectStderr) {
                createEmptyFile(outputFile);
                System.out.println(output);
            } else {
                System.out.println(output);
            }
            return;
        }

        String path = findExecutable(command);

        if (path != null) {
            executeExternal(command, path, argsArr, redirectStdout, redirectStderr, 
                           outputFile, appendMode, background);
        } else {
            String errorMsg = command + ": command not found";
            if (redirectOutput && redirectStderr) {
                writeToFile(outputFile, errorMsg, appendMode);
            } else if (redirectOutput && redirectStdout) {
                System.err.println(errorMsg);
            } else {
                System.out.println(errorMsg);
            }
        }
    }

    static void runBackgroundJob(String command, String[] argsArr, String fullCommandString,
                                 boolean redirectOutput, boolean redirectStdout,
                                 boolean redirectStderr, String outputFile, 
                                 boolean appendMode) {
        String path = findExecutable(command);
        
        if (path == null) {
            System.out.println(command + ": command not found");
            return;
        }
        
        int jobId = getNextJobNumber();
        
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        
        Thread backgroundThread = new Thread(() -> {
            try {
                List<String> cmd = new ArrayList<>();
                cmd.add(command);
                cmd.addAll(Arrays.asList(argsArr));

                ProcessBuilder pb = new ProcessBuilder(cmd);
                
                if (outputFile != null && (redirectStdout || redirectStderr)) {
                    File file = new File(outputFile);
                    File parent = file.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    
                    if (redirectStdout && redirectStderr) {
                        if (appendMode) {
                            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(file));
                            pb.redirectError(ProcessBuilder.Redirect.appendTo(file));
                        } else {
                            pb.redirectOutput(file);
                            pb.redirectError(file);
                        }
                    } else if (redirectStdout) {
                        if (appendMode) {
                            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(file));
                        } else {
                            pb.redirectOutput(file);
                        }
                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    } else if (redirectStderr) {
                        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                        if (appendMode) {
                            pb.redirectError(ProcessBuilder.Redirect.appendTo(file));
                        } else {
                            pb.redirectError(file);
                        }
                    }
                } else {
                    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                }

                Process process = pb.start();
                
                BackgroundJob job = new BackgroundJob(jobId, fullCommandString, process);
                synchronized (backgroundJobs) {
                    backgroundJobs.add(job);
                }
                
                System.out.println("[" + jobId + "] " + process.pid());
                
                latch.countDown();
                
                process.waitFor();
                
                synchronized (backgroundJobs) {
                    job.completed = true;
                    job.status = "Done";
                }
                
            } catch (Exception e) {
                System.err.println("Error in background job: " + e.getMessage());
                latch.countDown();
            }
        });
        
        backgroundThread.setDaemon(true);
        backgroundThread.start();
        
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static void createEmptyFile(String filename) {
        try {
            File file = new File(filename);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            
            if (!file.exists()) {
                file.createNewFile();
            }
        } catch (IOException e) {
            System.err.println("Error creating file: " + e.getMessage());
        }
    }

    static void writeToFile(String filename, String content, boolean append) {
        try {
            File file = new File(filename);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            
            try (FileWriter writer = new FileWriter(file, append)) {
                writer.write(content);
                if (!content.endsWith("\n")) {
                    writer.write("\n");
                }
            }
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }

    static List<String> parseInput(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false;
        int i = 0;
        
        while (i < input.length()) {
            char c = input.charAt(i);
            
            if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
                i++;
                continue;
            }
            
            if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
                i++;
                continue;
            }
            
            if (inSingleQuotes) {
                currentToken.append(c);
                i++;
                continue;
            }
            
            if (inDoubleQuotes) {
                if (c == '\\') {
                    if (i + 1 < input.length()) {
                        char nextChar = input.charAt(i + 1);
                        if (nextChar == '"' || nextChar == '\\') {
                            currentToken.append(nextChar);
                            i += 2;
                        } else {
                            currentToken.append(c);
                            i++;
                        }
                    } else {
                        currentToken.append(c);
                        i++;
                    }
                } else {
                    currentToken.append(c);
                    i++;
                }
                continue;
            }
            
            if (c == '\\') {
                if (i + 1 < input.length()) {
                    char nextChar = input.charAt(i + 1);
                    currentToken.append(nextChar);
                    i += 2;
                } else {
                    currentToken.append(c);
                    i++;
                }
                continue;
            }
            
            if (c == ' ' || c == '\t') {
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken = new StringBuilder();
                }
                i++;
                continue;
            }
            
            currentToken.append(c);
            i++;
        }
        
        if (currentToken.length() > 0) {
            tokens.add(currentToken.toString());
        }
        
        return tokens;
    }

    static String findExecutable(String command) {
        String pathEnv = System.getenv("PATH");

        if (pathEnv == null)
            return null;

        String[] paths = pathEnv.split(":");

        for (String pathDir : paths) {
            File file = new File(pathDir + "/" + command);

            if (file.exists() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }

        return null;
    }

    static void executeExternal(String command, String path, String[] args, 
                                boolean redirectStdout, boolean redirectStderr, 
                                String outputFile, boolean appendMode, 
                                boolean background) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(command);
            cmd.addAll(Arrays.asList(args));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            
            if (outputFile != null && (redirectStdout || redirectStderr)) {
                File file = new File(outputFile);
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                
                if (redirectStdout && redirectStderr) {
                    if (appendMode) {
                        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(file));
                        pb.redirectError(ProcessBuilder.Redirect.appendTo(file));
                    } else {
                        pb.redirectOutput(file);
                        pb.redirectError(file);
                    }
                } else if (redirectStdout) {
                    if (appendMode) {
                        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(file));
                    } else {
                        pb.redirectOutput(file);
                    }
                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                } else if (redirectStderr) {
                    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    if (appendMode) {
                        pb.redirectError(ProcessBuilder.Redirect.appendTo(file));
                    } else {
                        pb.redirectError(file);
                    }
                }
            } else {
                pb.inheritIO();
            }

            Process process = pb.start();
            process.waitFor();

        } catch (Exception e) {
            System.err.println("Error executing command: " + e.getMessage());
        }
    }
}