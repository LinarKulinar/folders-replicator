/*
 * Copyright (c) 2008, 2010, Oracle and/or its affiliates. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *   - Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *
 *   - Redistributions in binary form must reproduce the above copyright
 *     notice, this list of conditions and the following disclaimer in the
 *     documentation and/or other materials provided with the distribution.
 *
 *   - Neither the name of Oracle nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
 * IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

//import deprecated.WatchFileEvent;
//import deprecated.WatchFileType;

import java.nio.file.*;
import static java.nio.file.StandardWatchEventKinds.*;
import static java.nio.file.LinkOption.*;
import java.nio.file.attribute.*;
import java.io.*;
import java.util.*;

/**
 * Пример просмотра каталога (или дерева) на наличие изменений в файлах.
 * (Example to watch a directory (or tree) for changes to files.)
 */

public class WatchDir {

    private final WatchService watcher;  // Служба наблюдения, отслеживающая зарегистрированные объекты на предмет изменений
    private final Map<WatchKey,Path> keys;  // маппинг токена наблюдателя Watchkeys с объектом Path
    private final boolean recursive;
    private boolean trace = false;
    private Replicator replicator;  // Сервис, осуществляющий репликацию

    @SuppressWarnings("unchecked")
    static <T> WatchEvent<T> cast(WatchEvent<?> event) {
        return (WatchEvent<T>)event;
    }

    /**
     * Зарегистрируем данный каталог в WatchService
     * (Register the given directory with the WatchService)
     */
    private void register(Path dir) throws IOException {
        WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        if (trace) {
            Path prev = keys.get(key);
            if (prev == null) {
                System.out.format("register: %s\n", dir);
            } else {
                if (!dir.equals(prev)) {
                    System.out.format("update: %s -> %s\n", prev, dir);
                }
            }
        }
        keys.put(key, dir);
    }

    /**
     * Зарегистрируем данный каталог и все его подкаталоги в WatchService.
     * (Register the given directory, and all its sub-directories, with the WatchService.)
     */
    private void registerAll(final Path start) throws IOException {
        // Регистрируем каталог и все его подкаталоги
        // register directory and sub-directories
        Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                    throws IOException
            {
                register(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Создает WatchService и регистрирует данный каталог
     * (Creates a WatchService and registers the given directory)
     */
    WatchDir(String source, boolean recursive, Replicator replicator) throws IOException {
        Path dir = Paths.get(source);
        this.watcher = FileSystems.getDefault().newWatchService();
        this.keys = new HashMap<WatchKey,Path>();
        this.recursive = recursive;
        this.replicator = replicator;

        if (recursive) {
            System.out.format("Scanning %s ...\n", dir);
            registerAll(dir);
            System.out.println("Done.");
        } else {
            register(dir);
        }

        // enable trace after initial registration
        this.trace = true;
    }

    /**
     * Обрабатывать все события для ключей, поставленных в очередь наблюдателя.
     * (Process all events for keys queued to the watcher)
     */
    void processEvents() {
        for (;;) {

            // дождитесь сигнала ключа
            // (wait for key to be signalled)
            WatchKey key;
            try {
                key = watcher.take();  // Извлекает и удаляет следующий ключ наблюдения, ожидая, если его еще нет.
            } catch (InterruptedException x) {
                return;
            }

            Path dir = keys.get(key);
            if (dir == null) {
                System.err.println("WatchKey not recognized!!");
                continue;
            }

            for (WatchEvent<?> event: key.pollEvents()) { //События извлекаются путем вызова метода pollEvents ключа. Этот метод извлекает и удаляет все события, накопленные для объекта.
                WatchEvent.Kind kind = event.kind();

                //todo: надо обработать переименование файла
                //todo: надо обработать переименование папки
                //todo: надо оптимизировать модицикацию файла

                // Требует уточнения - приведения примера события OVERFLOW
                // (TBD - provide example of how OVERFLOW event is handled)
                if (kind == OVERFLOW) {
                    continue;
                }

                // Контекст для события создания записи(entry event) это имя файла записи (entry)
                WatchEvent<Path> ev = cast(event);
                Path name = ev.context();
                Path child = dir.resolve(name);

                // Принтим событие
                System.out.format("%s: %s\n", event.kind().name(), child);
                replicator.replicate(child, kind);


                // Если директория создана и просматривается рекурсивно, то
                // регистрируем его и его подкаталоги
                if (recursive && (kind == ENTRY_CREATE)) {
                    try {
                        if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
                            registerAll(child);
                        }
                    } catch (IOException x) {
                        // игнорировать, чтобы образец оставался читаемым
                    }
                }
            }

            // сбросить ключ и удалить из набора, если каталог больше не доступен
            // (reset key and remove from set if directory no longer accessible)
            boolean valid = key.reset();  // Вызывать, только когда потоки закончили свою работу
            if (!valid) {
                keys.remove(key);

                // все каталоги недоступны
                // (all directories are inaccessible)
                if (keys.isEmpty()) {
                    break;
                }
            }
        }
    }
}