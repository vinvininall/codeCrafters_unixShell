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
                
            String command = tokens.get(0);
            String[] argsArr = tokens.size() > 1 ? 
                tokens.subList(1, tokens.size()).toArray(new String[0]) : 
                new String[0];

            // echo builtin
            if (command.equals("echo")) {
                System.out.println(String.join(" ", argsArr));
                continue;
            }

            // pwd builtin
            if (command.equals("pwd")) {
                String cwd = System.getProperty("user.dir");
                System.out.println(cwd);
                continue;
            }

            // cd builtin
            if (command.equals("cd")) {
                if (argsArr.length == 0) {
                    // If no argument, cd to home directory
                    String home = System.getenv("HOME");
                    if (home == null) {
                        home = System.getProperty("user.home");
                    }
                    System.setProperty("user.dir", home);
                } else {
                    String newPath = argsArr[0];
                    
                    try {
                        // Get the current working directory
                        String currentDir = System.getProperty("user.dir");
                        
                        // Create a File object for the new path
                        File newDir;
                        
                        // Handle ~ (home directory)
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
                            System.out.println("cd: " + newPath + ": No such file or directory");
                        }
                        
                    } catch (IOException e) {
                        System.out.println("cd: " + newPath + ": Error changing directory");
                    }
                }
                continue;
            }

            // type builtin
            if (command.equals("type")) {
                String cmd = argsArr.length > 0 ? argsArr[0] : "";

                if (builtins.contains(cmd)) {
                    System.out.println(cmd + " is a shell builtin");
                } else {
                    String path = findExecutable(cmd);

                    if (path != null) {
                        System.out.println(cmd + " is " + path);
                    } else {
                        System.out.println(cmd + ": not found");
                    }
                }
                continue;
            }

            // external command execution
            String path = findExecutable(command);

            if (path != null) {
                executeExternal(command, path, argsArr);
            } else {
                System.out.println(command + ": command not found");
            }
        }
    }

    static List<String> parseInput(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder currentToken = new StringBuilder();
        boolean inSingleQuotes = false;
        boolean inDoubleQuotes = false; // For future expansion
        int i = 0;
        
        while (i < input.length()) {
            char c = input.charAt(i);
            
            if (c == '\'' && !inDoubleQuotes) {
                // Toggle single quotes
                inSingleQuotes = !inSingleQuotes;
                i++;
                continue;
            }
            
            if (inSingleQuotes) {
                // Inside single quotes, take everything literally
                currentToken.append(c);
                i++;
                continue;
            }
            
            // Outside quotes
            if (c == ' ' || c == '\t') {
                // Whitespace - end current token if not empty
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
        
        // Add the last token if not empty
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

    static void executeExternal(String command, String path, String[] args) {
        try {
            List<String> cmd = new ArrayList<>();

            // Only add the command name, NOT the full path
            cmd.add(command);
            
            // Add the rest of the arguments
            cmd.addAll(Arrays.asList(args));

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.inheritIO();

            Process process = pb.start();
            process.waitFor();

        } catch (Exception e) {
            System.out.println("Error executing command: " + e.getMessage());
        }
    }
}