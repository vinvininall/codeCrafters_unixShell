import java.util.*;
import java.io.*;

public class Main {

    static Set<String> builtins = new HashSet<>(
            Arrays.asList("echo", "exit", "type", "pwd", "cd")); // Add "cd" here

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            String input = sc.nextLine().trim();

            if (input.equals("exit") || input.equals("exit 0")) {
                return;
            }

            if (input.isEmpty())
                continue;

            String[] parts = input.split(" ");
            String command = parts[0];
            String[] argsArr = Arrays.copyOfRange(parts, 1, parts.length);

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
        String home = System.getProperty("user.home");
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
                String home = System.getProperty("user.home");
                if (newPath.equals("~")) {
                    newDir = new File(home);
                } else {
                    // ~/something format
                    newDir = new File(home + newPath.substring(1));
                }
            } else if (newPath.startsWith("/")) {
                // Absolute path
                newDir = new File(newPath);
            } else {
                // Relative path
                newDir = new File(currentDir, newPath);
            }
            
            // Get the canonical path (resolves .., ., symlinks)
            String canonicalPath = newDir.getCanonicalPath();
            File canonicalFile = new File(canonicalPath);
            
            // Check if the directory exists and is a directory
            if (canonicalFile.exists() && canonicalFile.isDirectory()) {
                // Change to the resolved absolute path
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

            String path = findExecutable(command);

            if (path != null) {
                executeExternal(command, path, argsArr);
            } else {
                System.out.println(command + ": command not found");
            }
        }
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

            // this is to make sure that Only add the command name, NOT the full path
            cmd.add(command);

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