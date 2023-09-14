package ru.otus.example.images.services;

import ru.otus.example.images.exceptions.FileNotFoundException;
import ru.otus.example.images.exceptions.UnsupportedMediaTypeException;
import ru.otus.example.images.persistence.entities.Image;
import ru.otus.example.images.persistence.repositories.ImageRepository;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
public class ImageService {

    private final ImageRepository imageRepository;

    private final Path fileStorageLocationPhoto;

    public ImageService(ImageRepository imageRepository,
            @Value("${upload.image-path.photo}") String PHOTO_UPLOAD_PATH) {
        this.fileStorageLocationPhoto = Paths.get(PHOTO_UPLOAD_PATH).toAbsolutePath().normalize();
        this.imageRepository = imageRepository;
    }

    public String uploadImage(MultipartFile image, UUID id) throws IOException, UnsupportedMediaTypeException {
        return switch (Objects.requireNonNull(image.getContentType())) {
            case MediaType.IMAGE_JPEG_VALUE -> determineImageType(image, id, MediaType.IMAGE_JPEG.getSubtype());
            case MediaType.IMAGE_PNG_VALUE -> determineImageType(image, id, MediaType.IMAGE_PNG.getSubtype());
            default -> throw new UnsupportedMediaTypeException("Error! This file type is not supported!");
        };
    }

    public Resource obtainPhoto(UUID id) {
        return imageRepository.findById(id).map(image -> loadFileAsResource(image.getName())).orElse(null);
    }

    private String determineImageType(MultipartFile image, UUID id, String fileExtension) throws IOException {
        return processImageForPlace(image, id, UUID.randomUUID() + "." + fileExtension);
    }

    private String processImageForMember(MultipartFile image, UUID memberId, String uploadedFileName) throws IOException {

        Image oldImage = deletePreviousMemberPhotoFromStorage(memberId);

        copyImageFileToServer(uploadedFileName, image);

        Image img = imageRepository.save(new Image(uploadedFileName));
        //memberService.updateMemberPhoto(memberId, img);

        if (oldImage != null) {
            imageRepository.deleteById(oldImage.getId());
        }

        return img.getId().toString();

    }

    private String processImageForPlace(MultipartFile image, UUID placeId, String uploadedFileName) throws IOException {

        copyImageFileToServer(uploadedFileName, image);

        Image img = imageRepository.save(new Image(uploadedFileName));
        //placeService.updatePlacePhoto(placeId, img);

        return img.getId().toString();

    }

    private void copyImageFileToServer(String uploadedFileName, MultipartFile image) throws IOException {
        Files.copy(image.getInputStream(), this.fileStorageLocationPhoto.resolve(uploadedFileName), StandardCopyOption.REPLACE_EXISTING);
    }

    private Resource loadFileAsResource(final String imageFileName) {

        Path filePath;

        try {

            filePath = this.fileStorageLocationPhoto.resolve(imageFileName).normalize();

            Resource resource = new UrlResource(filePath.toUri());
            if (resource.exists()) {
                return resource;
            } else {
                throw new FileNotFoundException("File " + imageFileName + " not found!");
            }
        } catch (MalformedURLException ex) {
            throw new FileNotFoundException("File " + imageFileName + " not found !");
        }
    }

    private Image deletePreviousMemberPhotoFromStorage(UUID id) {

        Image oldImage = null;
        Optional<Image> imageOptional = imageRepository.findById(id);

        if (imageOptional.isPresent()) {
            oldImage = imageOptional.get();
            deleteFileFromDisk(oldImage.getName(), this.fileStorageLocationPhoto);
        }

        return oldImage;

    }

    @Transactional
    public void deleteImage(UUID id) {
        Optional<Image> imageOptional = imageRepository.findById(id);
        if (imageOptional.isPresent()) {
            deleteFileFromDisk(imageOptional.get().getName(), this.fileStorageLocationPhoto);
            imageRepository.deleteById(id);
        }
    }

    private void deleteFileFromDisk(String imageName, Path path) {

        Path filePath = path.resolve(imageName).normalize();

        log.info(filePath.toFile().delete() ?
                "File " + filePath.getFileName() + " has been succesfully deleted!" :
                "Failed to delete file: " + filePath.getFileName());

    }

}