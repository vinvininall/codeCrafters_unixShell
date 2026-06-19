import java.util.*;

public class Main {

    static Set<String> builtins = new HashSet<>(
            Arrays.asList("echo", "exit", "type")
    );

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");
            System.out.flush();

            String input = sc.nextLine().trim();

            if (input.equals("exit") || input.equals("exit 0")) {
                return;
            }

            if (input.startsWith("echo ")) {
                System.out.println(input.substring(5));
                continue;
            }

            if (input.startsWith("type ")) {
                String cmd = input.substring(5).trim();

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

            if (!input.isEmpty()) {
                System.out.println(input + ": command not found");
            }
        }
    }

    static String findExecutable(String command) {
        String pathEnv = System.getenv("PATH");

        if (pathEnv == null) return null;

        String[] paths = pathEnv.split(":"); // Linux-style PATH (required for tests)

        for (String pathDir : paths) {
            java.io.File file = new java.io.File(pathDir + "/" + command);

            if (file.exists() && file.canExecute()) {
                return file.getAbsolutePath();
            }
        }

        return null;
    }
}