package ru.otus.example.images.persistence.repositories;

import ru.otus.example.images.persistence.entities.Image;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ImageRepository extends JpaRepository<Image, UUID> {}