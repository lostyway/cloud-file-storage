package com.lostway.cloudfilestorage.utils;

import com.lostway.cloudfilestorage.exception.dto.CantGetUserContextIdException;
import com.lostway.cloudfilestorage.exception.dto.InvalidFolderPathException;
import com.lostway.cloudfilestorage.security.CustomUserDetails;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@UtilityClass
public class MinioStorageUtils {

    /**
     * Получение имени папки/файла, исключая его путь.
     *
     * @param folderPath полный путь до папки/файла
     * @return Имя файла/папки test/test2.txt --> test2.txt
     */
    public String getNameFromPath(String folderPath) {
        String trimmedPath = folderPath.endsWith("/")
                ? folderPath.substring(0, folderPath.length() - 1)
                : folderPath;

        String[] parts = trimmedPath.split("/");
        return parts[parts.length - 1];
    }

    /**
     * Получение полного имени ресурса
     */
    public String getOriginalFileName(MultipartFile file) {
        String originalFileName = file.getOriginalFilename();

        if (originalFileName == null || originalFileName.isBlank()) {
            throw new IllegalArgumentException("Имя файла отсутствует");
        }
        return originalFileName;
    }

    /**
     * Проверка, является ли путь путем до папки или файла
     *
     * @param path путь до ресурса
     * @return true --> это папка<p>
     * false --> это файл
     */
    public boolean isFolderPath(String path) {
        if (path.endsWith("/")) {
            return true;
        }

        int lastSlashIndex = path.lastIndexOf('/');
        String lastSegment = lastSlashIndex == -1 ? path : path.substring(lastSlashIndex + 1);

        return !lastSegment.contains(".");
    }


    /**
     * Возвращает путь до файла не включая этот файл<p>
     * test/test2.txt --> rootFolder/test/
     *
     * @param folderPath путь до папки (включая файл)
     * @return путь до папки, не включая сам файл/папку<p>
     * test/test2.txt --> rootFolder/test/<p>
     * test/test2/ --> rootFolder/test/
     */
    public String getParentFolders(String folderPath) {
        int lastFlash = folderPath.lastIndexOf("/", folderPath.length() - 2);
        if (lastFlash > 0) {
            return folderPath.substring(0, lastFlash + 1);
        }
        return null;
    }

    /**
     * Проверка пути до папки
     *
     * @param folderPath путь к папке (используется только для папок, для файлов не подойдет)
     */
    public void checkFolderPath(String folderPath) {
        if (!folderPath.matches("^(?!.*//)(?!.*\\.{1,2})([\\p{L}\\d _-]+/)*$")) {
            throw new InvalidFolderPathException("Недопустимый путь к папке: " + folderPath);
        }
    }

    /**
     * Проверка пути до файла
     *
     * @param path путь к файлу (используется только для файлов, для папок не подойдет)
     */
    public void validatePathToFile(String path) {
        if (!path.matches("^(?!.*//)(?!.*(?:^|/)\\.)(?!.*(?:^|/)\\.\\.)([\\p{L}\\p{N} _\\-]+/)*[\\p{L}\\p{N} _\\-]+\\.[\\p{L}\\p{N}]+$")) {
            throw new InvalidFolderPathException("Недопустимый путь: " + path);
        }
    }

    public String getStandardPath(String path) {
        if (path == null || path.isBlank()) {
            path = "";
        }

        return path.replaceAll("^/+", "");
    }

    /**
     * Получение userID текущего пользователя.
     *
     * @return UserID из контекста безопасности
     */
    private Long getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal instanceof CustomUserDetails userDetails) {
            return userDetails.getId();
        }
        throw new CantGetUserContextIdException("Аутентифицированный пользователь не найден или имеет неверный тип");
    }

    /**
     * Получение корневой папки по userId из контекста.
     *
     * @return ID текущего пользователя
     */
    public String getRootFolder() {
        Long userId = getCurrentUserId();
        return "user-" + userId + "-files/";
    }

    /**
     * Получение полного пути до ресурса пользователя (должен вызываться первым делом, чтобы получать root папку пользователя)
     */
    public String getFullUserPath(String path) {
        String newPath = getStandardFullRootFolder(path);
        return isFolderPath(newPath) && !newPath.endsWith("/") ? newPath + "/" : newPath;
    }

    private String getStandardFullRootFolder(String path) {
        String newPath = getStandardPath(path);
        return getRootFolder() + newPath;
    }

    /**
     * Являются ли два пути одним и тем же типом (папка+папка, файл+файл).
     * Сделано для того, чтобы не могли прислать путь до файла и изменить его на папку
     *
     * @param oldPath Полный путь до старого места ресурса
     * @param newPath Полный путь до нового места ресурса
     * @return Вердикт. Ведут ли два пути к одному типу ресурса
     */
    public boolean isSameType(String oldPath, String newPath) {
        return isFolderPath(oldPath) == isFolderPath(newPath);
    }

    /**
     * Проверка, является ли путь пустым, "/". Т.е. просто корнем файловой системы
     *
     * @param path путь для проверки
     * @return Вердикт. Ведет ли путь к корневой директории или нет
     */
    public boolean isRootFolder(String path) {
        return path == null || path.isBlank() || path.trim().equals("/");
    }

    /**
     * Проверка путей вне зависимости от того файл это или папка
     *
     * @param path путь к ресурсу
     */
    public void validateResourcePath(String path) {
        if (isFolderPath(path)) {
            checkFolderPath(path);
        } else {
            validatePathToFile(path);
        }
    }
}
