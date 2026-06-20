import java.util.*;
import java.io.*;

public class Main {

    static Set<String> builtins = new HashSet<>(
            Arrays.asList("echo", "exit", "type", "pwd", "cd", "jobs"));

    // Keep track of background jobs
    static List<BackgroundJob> backgroundJobs = new ArrayList<>();
    static int jobCounter = 0;

    static class BackgroundJob {
        int id;
        String command;
        Process process;
        boolean completed;

        BackgroundJob(int id, String command, Process process) {
            this.id = id;
            this.command = command;
            this.process = process;
            this.completed = false;
        }
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
                // Clean up completed jobs and print running ones
                List<BackgroundJob> activeJobs = new ArrayList<>();
                for (BackgroundJob job : backgroundJobs) {
                    if (job.process != null && job.process.isAlive()) {
                        activeJobs.add(job);
                    } else {
                        job.completed = true;
                    }
                }
                backgroundJobs = activeJobs;
                
                if (!backgroundJobs.isEmpty()) {
                    for (int i = 0; i < backgroundJobs.size(); i++) {
                        BackgroundJob job = backgroundJobs.get(i);
                        // Format: [jobId]+  Running                 command &
                        // The + indicates the most recent job
                        String marker = (i == backgroundJobs.size() - 1) ? "+" : "-";
                        
                        // Status field padded to 24 characters total
                        String status = "Running";
                        // Pad to 24 characters (7 for "Running" + 17 spaces)
                        String paddedStatus = String.format("%-24s", status);
                        
                        // Command with trailing & (to indicate background job)
                        String cmdWithBg = job.command + " &";
                        
                        System.out.println("[" + job.id + "]" + marker + "  " + paddedStatus + cmdWithBg);
                    }
                }
                // If no jobs, print nothing
                continue;
            }

            boolean isBuiltin = builtins.contains(command);
            
            if (isBuiltin || !background) {
                executeCommand(command, argsArr, redirectOutput, redirectStdout, 
                              redirectStderr, outputFile, appendMode, background, isBuiltin);
            } else {
                // Run external command in background
                runBackgroundJob(command, argsArr, fullCommandString, redirectOutput, redirectStdout, 
                               redirectStderr, outputFile, appendMode);
            }
        }
    }

    static void executeCommand(String command, String[] argsArr, 
                               boolean redirectOutput, boolean redirectStdout,
                               boolean redirectStderr, String outputFile, 
                               boolean appendMode, boolean background, boolean isBuiltin) {
        // echo builtin
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

        // pwd builtin
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

        // cd builtin
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

        // type builtin
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

        // external command execution
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
        
        // Create a new job ID (sequential starting from 1)
        int jobId = ++jobCounter;
        
        // Use a latch to wait for the PID to be printed
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
                
                // Store the job with the full command string
                BackgroundJob job = new BackgroundJob(jobId, fullCommandString, process);
                synchronized (backgroundJobs) {
                    backgroundJobs.add(job);
                }
                
                // Print the job notification: [jobId] pid
                System.out.println("[" + jobId + "] " + process.pid());
                
                // Signal that the PID has been printed
                latch.countDown();
                
                process.waitFor();
                
                // Mark job as completed
                synchronized (backgroundJobs) {
                    job.completed = true;
                }
                
            } catch (Exception e) {
                System.err.println("Error in background job: " + e.getMessage());
                latch.countDown(); // Ensure we don't block forever
            }
        });
        
        backgroundThread.setDaemon(true);
        backgroundThread.start();
        
        // Wait for the PID to be printed before continuing
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