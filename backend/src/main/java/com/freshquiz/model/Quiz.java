package com.freshquiz.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "quizzes")
@Getter
@Setter
@NoArgsConstructor
public class Quiz {

	@Id
	@Column(name = "id", nullable = false)
	private String id;

	@Column(name = "title", nullable = false)
	private String title;

	@Column(name = "owner_token_hash")
	private String ownerTokenHash;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
	@OrderBy("position ASC")
	private List<Question> questions = new ArrayList<>();

	public Quiz(String id, String title, String ownerTokenHash, Instant createdAt) {
		this.id = id != null ? id : UUID.randomUUID().toString();
		this.title = title;
		this.ownerTokenHash = ownerTokenHash;
		this.createdAt = createdAt != null ? createdAt : Instant.now();
	}

	public void updateTitle(String title) {
		this.title = title;
	}

	public Question addQuestion(String prompt, int durationSeconds) {
		int nextPos = questions.size() + 1;
		Question question = new Question(UUID.randomUUID().toString(), this, nextPos, prompt, durationSeconds);
		questions.add(question);
		return question;
	}

	public void removeQuestion(String questionId) {
		questions.removeIf(q -> q.getId().equals(questionId));
		for (int i = 0; i < questions.size(); i++) {
			questions.get(i).setPosition(i + 1);
		}
	}

	public boolean isOwner(String token) {
		if (this.ownerTokenHash == null || this.ownerTokenHash.isBlank()) {
			return false;
		}
		if (token == null || token.isBlank()) {
			return false;
		}
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] hash = md.digest(token.trim().getBytes(StandardCharsets.UTF_8));
			String calculatedHash = HexFormat.of().formatHex(hash).toLowerCase(Locale.ROOT);
			return calculatedHash.equalsIgnoreCase(this.ownerTokenHash.trim());
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 indisponivel", e);
		}
	}

	public boolean canDelete() {
		return true;
	}
}
