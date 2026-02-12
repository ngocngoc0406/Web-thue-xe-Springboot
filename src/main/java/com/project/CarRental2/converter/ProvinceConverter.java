package com.project.CarRental2.converter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import com.project.CarRental2.model.Province;
import com.project.CarRental2.repository.ProvinceRepository;

@Component
public class ProvinceConverter implements Converter<String, Province> {

    @Autowired
    private ProvinceRepository provinceRepository;

    @Override
    public Province convert(String source) {
        if (source == null || source.isEmpty() || source.equals("0")) {
            return null;
        }
        try {
            int id = Integer.parseInt(source);
            return provinceRepository.findById(id).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
