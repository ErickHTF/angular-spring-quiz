package com.freshquiz.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import com.freshquiz.game.model.GameStatus;

@Converter(autoApply = true)
public class GameStatusConverter implements AttributeConverter<GameStatus, String> {

	@Override
	public String convertToDatabaseColumn(GameStatus attribute) {
		return attribute == null ? null : attribute.value();
	}

	@Override
	public GameStatus convertToEntityAttribute(String dbData) {
		return dbData == null ? null : GameStatus.fromValue(dbData);
	}
}
