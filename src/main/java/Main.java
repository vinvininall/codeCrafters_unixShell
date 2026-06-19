import java.util.Scanner;

// public class Main {
//     public static void main(String[] args){
//         Scanner sc = new Scanner(System.in);
//         while(true){
//             System.out.print("$ ");
//             System.out.flush();
//             String input = sc.nextLine();
//             if(input.equals("exit 0")){
//                 break;
//             }
//             System.out.println(input);
//         }
//     }
// }


public class Main {
    public static void main(String[] args) throws Exception {
        System.out.print("$ ");
        Scanner scanner = new Scanner(System.in);
        String input = scanner.nextLine();
        System.out.println(input + ": command not found");
    }
}