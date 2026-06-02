package com.project.CarRental2.service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;

@Service
public class UploadFileImpl implements UploadFile {
	private final static int LENGTH_MAX = 7;
	private Path uploadPath;

	@PostConstruct
	public void init() {
		uploadPath = Paths.get("uploads").toAbsolutePath().normalize();
		System.out.println("[UPLOAD] Upload directory: " + uploadPath.toString());
		try {
			if (Files.notExists(uploadPath)) {
				Files.createDirectories(uploadPath);
				System.out.println("[UPLOAD] Created upload directory: " + uploadPath.toString());
			}
		} catch (Exception e) {
			System.err.println("[UPLOAD] ERROR creating upload directory: " + e.getMessage());
			e.printStackTrace();
		}
	}

	private String generateFileName() {
		Date date = new Date();
		SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
		String d = formatter.format(date);

		String alphanumericCharacters = "0123456789abcdefghijklmnopqrstuv";
		StringBuffer randomString = new StringBuffer(LENGTH_MAX);
		Random random = new Random();
		for (int i = 0; i < LENGTH_MAX; i++) {
			int randomIndex = random.nextInt(alphanumericCharacters.length());
			char randomChar = alphanumericCharacters.charAt(randomIndex);
			randomString.append(randomChar);
		}

		return d + randomString + ".jpg";
	}

	@Override
	public String uploadSingleFile(MultipartFile file) {
		String fileName = "";
		try {
			if (file == null || file.isEmpty()) {
				System.err.println("[UPLOAD] File is null or empty");
				return fileName;
			}
			InputStream inputStream = file.getInputStream();
			fileName = generateFileName();
			System.out.println("[UPLOAD] Saving single file: " + fileName + " to " + uploadPath.toString());
			Files.copy(inputStream, uploadPath.resolve(fileName),
					StandardCopyOption.REPLACE_EXISTING);
			System.out.println("[UPLOAD] File saved successfully: " + uploadPath.resolve(fileName).toString());
		} catch (Exception e) {
			System.err.println("[UPLOAD] ERROR saving file: " + e.getMessage());
			e.printStackTrace();
		}
		return fileName;
	}

	@Override
	public void removeFile(String nameFile) {
		try {
			if (nameFile != null && !nameFile.isEmpty()) {
				Files.delete(uploadPath.resolve(nameFile));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public String uploadMultiFile(MultipartFile[] files) {
		String imgName = "";

		for (MultipartFile f : files) {
			if (f == null || f.isEmpty()) {
				continue;
			}
			try {
				InputStream inputStream = f.getInputStream();
				String fileName = generateFileName();
				System.out.println("[UPLOAD] Saving multi file: " + fileName + " to " + uploadPath.toString());
				Files.copy(inputStream, uploadPath.resolve(fileName),
						StandardCopyOption.REPLACE_EXISTING);
				System.out.println("[UPLOAD] File saved successfully: " + uploadPath.resolve(fileName).toString());
				imgName = imgName + fileName + ";";
			} catch (Exception e) {
				System.err.println("[UPLOAD] ERROR saving multi file: " + e.getMessage());
				e.printStackTrace();
			}
		}
		if (imgName.length() > 0) {
			imgName = imgName.substring(0, imgName.length() - 1);
		}
		return imgName;
	}

	@Override
	public String uploadFileDocument(MultipartFile file) {
		String fileName = "";
		try {
			if (file == null || file.isEmpty()) {
				return fileName;
			}
			InputStream inputStream = file.getInputStream();
			fileName = file.getOriginalFilename().trim();
			System.out.println("[UPLOAD] Saving document: " + fileName);
			Files.copy(inputStream, uploadPath.resolve(fileName),
					StandardCopyOption.REPLACE_EXISTING);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return fileName;
	}
}
