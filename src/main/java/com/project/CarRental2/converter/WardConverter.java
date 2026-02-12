package com.project.CarRental2.converter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import com.project.CarRental2.model.Ward;
import com.project.CarRental2.repository.WardRepository;

@Component
public class WardConverter implements Converter<String, Ward> {

    @Autowired
    private WardRepository wardRepository;

    @Override
    public Ward convert(String source) {
        if (source == null || source.isEmpty() || source.equals("0")) {
            return null;
        }
        try {
            int id = Integer.parseInt(source);
            return wardRepository.findById(id).orElse(null);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
