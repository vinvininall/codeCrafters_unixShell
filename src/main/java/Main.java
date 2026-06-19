import java.util.*;
import java.io.*;

public class Main {

    static Set<String> builtins = new HashSet<>(
            Arrays.asList("echo", "exit", "type", "pwd"));

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