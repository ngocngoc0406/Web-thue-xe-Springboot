package com.project.CarRental2.configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.project.CarRental2.converter.BrandCarConverter;
import com.project.CarRental2.converter.DistrictConverter;
import com.project.CarRental2.converter.ProvinceConverter;
import com.project.CarRental2.converter.WardConverter;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

	@Autowired
	private BrandCarConverter brandCarConverter;

	@Autowired
	private ProvinceConverter provinceConverter;

	@Autowired
	private DistrictConverter districtConverter;

	@Autowired
	private WardConverter wardConverter;

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		exposeDirectory("uploads", registry);
	}

	@Override
	public void addFormatters(FormatterRegistry registry) {
		registry.addConverter(brandCarConverter);
		registry.addConverter(provinceConverter);
		registry.addConverter(districtConverter);
		registry.addConverter(wardConverter);
	}

	private void exposeDirectory(String dirName, ResourceHandlerRegistry registry) {
		// Hardcode the absolute path for Windows
		String uploadPath = "D:/CarRentail/CarRental/uploads/";

		// Also try relative path resolution as backup
		Path relativePath = Paths.get(dirName).toAbsolutePath();
		String resolvedPath = relativePath.toString().replace("\\", "/");
		if (!resolvedPath.endsWith("/")) {
			resolvedPath = resolvedPath + "/";
		}

		System.out.println("[CONFIG] Resolved relative path: " + resolvedPath);
		System.out.println("[CONFIG] Using hardcoded path: " + uploadPath);

		if (dirName.startsWith("../"))
			dirName = dirName.replace("../", "");

		// Use the hardcoded path
		String resourceLocation = "file:///" + uploadPath;
		registry.addResourceHandler("/" + dirName + "/**").addResourceLocations(resourceLocation);
		System.out.println("[CONFIG] Static Resource Handler: /" + dirName + "/** -> " + resourceLocation);
	}
}
