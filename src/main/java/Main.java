
import java.io.IOException;


public class Main {

    static void usage() {
        System.err.println("usage: java Main [-r] source target");
        System.exit(-1);
    }

    public static void main(String[] args) throws IOException {
        // Парсим аргументы
        if (args.length != 2 && args.length != 3)
            usage();
        boolean recursive = false;
        int sourceDirArg = 0;
        if ("-r".equals(args[0])) {
            if (args.length != 3)
                usage();
            recursive = true;
            sourceDirArg++;
        }

        String source = args[sourceDirArg];
        String target = args[sourceDirArg + 1];
        Replicator replicator = new Replicator(source, target);
        // // Регистрирунем директорию и обрабатываем события
        WatchDir watchDir = new WatchDir(source, recursive, replicator);
        watchDir.processEvents();
    }
}
