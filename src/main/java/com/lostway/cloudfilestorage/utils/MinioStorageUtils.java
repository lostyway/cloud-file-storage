package com.lostway.cloudfilestorage.utils;

import com.lostway.cloudfilestorage.exception.dto.InvalidFolderPathException;
import com.lostway.jwtsecuritylib.JwtUtil;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Component
public class MinioStorageUtils {

    /**
     * Получение имени папки/файла, исключая его путь.
     *
     * @param folderPath полный путь до папки/файла
     * @return Имя файла/папки test/test2.txt --> test2.txt
     */
    public static String getNameFromPath(String folderPath) {
        String trimmedPath = folderPath.endsWith("/")
                ? folderPath.substring(0, folderPath.length() - 1)
                : folderPath;

        String[] parts = trimmedPath.split("/");
        return parts[parts.length - 1];
    }

    /**
     * Получение полного имени ресурса
     */
    public static String getOriginalFileName(MultipartFile file) {
        String originalFileName = file.getOriginalFilename();

        if (originalFileName == null || originalFileName.isBlank()) {
            throw new IllegalArgumentException("Имя файла отсутствует");
        }
        return originalFileName;
    }

    /**
     * Проверка пути до файла
     *
     * @param path путь к файлу (используется только для файлов, для папок не подойдет)
     */
    public static void validatePathToFile(String path) {
        if (!path.matches("^(?!.*//)(?!.*(?:^|/)\\.)(?!.*(?:^|/)\\.\\.)([\\p{L}\\p{N} _\\-]+/)*[\\p{L}\\p{N} _\\-]+\\.[\\p{L}\\p{N}]+$")) {
            throw new InvalidFolderPathException("Недопустимый путь: " + path);
        }
    }

    public static String getStandardPath(String path) {
        if (path == null || path.isBlank()) {
            path = "";
        }

        return path.replaceAll("^/+", "");
    }

    /**
     * Получение корневой папки по userId из контекста.
     *
     * @return ID текущего пользователя
     */
    public static String getRootFolder(HttpServletRequest request, JwtUtil jwtUtil) {
        String token = jwtUtil.getTokenFromHeader(request)
                .orElseThrow(() -> new JwtException("JWT Token не был найден"));

        Long userId = jwtUtil.extractUserId(token);

        return "user-" + userId + "-files/";
    }

    public static String getStandardFullRootFolder(String path, HttpServletRequest request, JwtUtil jwtUtil) {
        String newPath = getStandardPath(path);
        return getRootFolder(request, jwtUtil) + newPath;
    }
}
