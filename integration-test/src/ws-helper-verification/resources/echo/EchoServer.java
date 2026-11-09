import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 供 WsHelper 一次性校验使用的 echo 服务。
 * <p>
 * 每条连接输出 ACCEPT/CLOSE，测试通过 docker logs 判断目标 socket 是否真正释放。
 */
public class EchoServer {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(args[0]);
        ServerSocket server = new ServerSocket(port, 50, InetAddress.getByName("0.0.0.0"));
        System.out.println("LISTENING " + port);
        System.out.flush();
        AtomicInteger ids = new AtomicInteger();
        while (true) {
            Socket socket = server.accept();
            int id = ids.incrementAndGet();
            System.out.println("ACCEPT " + id);
            System.out.flush();
            new Thread(() -> handle(id, socket), "echo-conn-" + id).start();
        }
    }

    private static void handle(int id, Socket socket) {
        try (Socket closeable = socket) {
            InputStream input = closeable.getInputStream();
            OutputStream output = closeable.getOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
                output.flush();
            }
        } catch (Throwable ignored) {
        }
        System.out.println("CLOSE " + id);
        System.out.flush();
    }
}
