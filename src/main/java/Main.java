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

            if (input.equals("exit 0")) {
                break;
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
                    System.out.println(cmd + ": not found");
                }
                continue;
            }

            // ✅ FIX IS HERE
            if (!input.isEmpty()) {
                System.out.println(input + ": command not found");
            }
        }
    }
}