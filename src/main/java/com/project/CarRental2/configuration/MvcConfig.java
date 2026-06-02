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
		// Use the same relative path that UploadFileImpl uses: Paths.get("uploads/")
		Path uploadPath = Paths.get("uploads").toAbsolutePath().normalize();
		String uploadDir = uploadPath.toUri().toString();

		System.out.println("[CONFIG] Upload resource location: " + uploadDir);

		registry.addResourceHandler("/uploads/**")
				.addResourceLocations(uploadDir);
	}

	@Override
	public void addFormatters(FormatterRegistry registry) {
		registry.addConverter(brandCarConverter);
		registry.addConverter(provinceConverter);
		registry.addConverter(districtConverter);
		registry.addConverter(wardConverter);
	}
}
