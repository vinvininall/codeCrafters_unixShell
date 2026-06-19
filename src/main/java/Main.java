import java.util.*;
import java.io.*;

public class Main {

    static Set<String> builtins = new HashSet<>(
            Arrays.asList("echo", "exit", "type", "pwd", "cd"));

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            String input = sc.nextLine();

            if (input.trim().equals("exit") || input.trim().equals("exit 0")) {
                return;
            }

            if (input.trim().isEmpty())
                continue;

            // Parse the input into command and arguments with quote support
            List<String> tokens = parseInput(input);
            
            if (tokens.isEmpty())
                continue;
            
            // Check for output redirection (> or 1> or 2>)
            int redirIndex = -1;
            String outputFile = null;
            boolean redirectStderr = false;
            
            for (int i = 0; i < tokens.size(); i++) {
                String token = tokens.get(i);
                if (token.equals(">") || token.equals("1>")) {
                    redirIndex = i;
                    redirectStderr = false;
                    if (i + 1 < tokens.size()) {
                        outputFile = tokens.get(i + 1);
                    }
                    break;
                } else if (token.equals("2>")) {
                    redirIndex = i;
                    redirectStderr = true;
                    if (i + 1 < tokens.size()) {
                        outputFile = tokens.get(i + 1);
                    }
                    break;
                }
            }
            
            // Extract command and arguments (excluding redirection tokens)
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

            // Check if we need to redirect output
            boolean redirectOutput = (redirIndex != -1 && outputFile != null);

            // echo builtin
            if (command.equals("echo")) {
                String output = String.join(" ", argsArr);
                if (redirectOutput) {
                    writeToFile(outputFile, output);
                } else {
                    System.out.println(output);
                }
                continue;
            }

            // pwd builtin
            if (command.equals("pwd")) {
                String cwd = System.getProperty("user.dir");
                if (redirectOutput) {
                    writeToFile(outputFile, cwd);
                } else {
                    System.out.println(cwd);
                }
                continue;
            }

            // cd builtin
            if (command.equals("cd")) {
                if (argsArr.length == 0) {
                    String home = System.getenv("HOME");
                    if (home == null) {
                        home = System.getProperty("user.home");
                    }
                    System.setProperty("user.dir", home);
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
                        } else {
                            String errorMsg = "cd: " + newPath + ": No such file or directory";
                            if (redirectOutput) {
                                writeToFile(outputFile, errorMsg);
                            } else {
                                System.out.println(errorMsg);
                            }
                        }
                        
                    } catch (IOException e) {
                        String errorMsg = "cd: " + newPath + ": Error changing directory";
                        if (redirectOutput) {
                            writeToFile(outputFile, errorMsg);
                        } else {
                            System.out.println(errorMsg);
                        }
                    }
                }
                continue;
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
                
                if (redirectOutput) {
                    writeToFile(outputFile, output);
                } else {
                    System.out.println(output);
                }
                continue;
            }

            // external command execution
            String path = findExecutable(command);

            if (path != null) {
                executeExternal(command, path, argsArr, redirectOutput, outputFile, redirectStderr);
            } else {
                String errorMsg = command + ": command not found";
                if (redirectOutput) {
                    writeToFile(outputFile, errorMsg);
                } else {
                    System.out.println(errorMsg);
                }
            }
        }
    }

    static void writeToFile(String filename, String content) {
        try {
            File file = new File(filename);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            
            try (FileWriter writer = new FileWriter(file)) {
                writer.write(content);
                if (!content.endsWith("\n")) {
                    writer.write("\n");
                }
            }
        } catch (IOException e) {
            System.out.println("Error writing to file: " + e.getMessage());
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
            
            // Handle single quotes (higher precedence than double quotes)
            if (c == '\'' && !inDoubleQuotes) {
                inSingleQuotes = !inSingleQuotes;
                i++;
                continue;
            }
            
            // Handle double quotes
            if (c == '"' && !inSingleQuotes) {
                inDoubleQuotes = !inDoubleQuotes;
                i++;
                continue;
            }
            
            // Inside single quotes - everything is literal
            if (inSingleQuotes) {
                currentToken.append(c);
                i++;
                continue;
            }
            
            // Inside double quotes - handle backslash escaping
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
            
            // Outside quotes - handle backslash escaping
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
            
            // Outside quotes
            if (c == ' ' || c == '\t') {
                if (currentToken.length() > 0) {
                    tokens.add(currentToken.toString());
                    currentToken = new StringBuilder();
                }
                i++;
                continue;
            }
            
            // Regular character
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
                                boolean redirectOutput, String outputFile, 
                                boolean redirectStderr) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(command);
            cmd.addAll(Arrays.asList(args));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            
            if (redirectOutput && outputFile != null) {
                File file = new File(outputFile);
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                
                if (redirectStderr) {
                    // Redirect stderr to file, stdout to terminal
                    pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    pb.redirectError(file);
                } else {
                    // Redirect stdout to file, stderr to terminal
                    pb.redirectOutput(file);
                    pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                }
            } else {
                // Inherit both stdout and stderr
                pb.inheritIO();
            }

            Process process = pb.start();
            int exitCode = process.waitFor();

        } catch (Exception e) {
            System.out.println("Error executing command: " + e.getMessage());
        }
    }
}