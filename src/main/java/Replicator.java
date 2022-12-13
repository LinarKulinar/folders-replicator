import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.WatchEvent;

import static java.nio.file.StandardWatchEventKinds.*;

public class Replicator {

    final String source;
    final String target;

    public Replicator(String source, String target) {
        this.source = source;
        this.target = target;
    }

    public void replicate(Path pathFrom, WatchEvent.Kind<Object> type){
        System.out.println("into replicate");
        try {
            if (ENTRY_CREATE.equals(type)) {
                createFile(pathFrom);
            } else if (ENTRY_DELETE.equals(type)) {
                deleteFile(pathFrom);
            } else if (ENTRY_MODIFY.equals(type)) {
                // todo: написать оптимальнее
                deleteFile(pathFrom);
                createFile(pathFrom);
            }
            // todo: обработать отдельно переименование файла
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void createFile(Path pathFrom) throws IOException {
        String filename = String.valueOf(pathFrom.getFileName());
        Path pathTo = (new File(target + '/' + filename)).toPath();
        // todo: обработать всякие эксепшены по документации
        Files.copy(pathFrom,
                pathTo,
                StandardCopyOption.REPLACE_EXISTING);
        System.out.println("success create file");
    }

    private void deleteFile(Path pathFrom) throws IOException {
        String filename = String.valueOf(pathFrom.getFileName());
        Path pathTo = (new File(target + '/' + filename)).toPath();
        // todo: обработать всякие эксепшены по документации
        Files.delete(pathTo);
        System.out.println("success delete file");
    }


}
