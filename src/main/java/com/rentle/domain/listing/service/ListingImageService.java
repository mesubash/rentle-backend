package com.rentle.domain.listing.service;

import com.rentle.config.RentleProperties;
import com.rentle.domain.listing.model.Listing;
import com.rentle.domain.listing.model.ListingImage;
import com.rentle.domain.listing.repository.ListingImageRepository;
import com.rentle.domain.listing.repository.ListingRepository;
import com.rentle.shared.exception.RentleException;
import com.rentle.shared.exception.ResourceNotFoundException;
import com.rentle.shared.exception.UnauthorizedException;
import com.rentle.shared.storage.ImageValidator;
import com.rentle.shared.storage.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ListingImageService {

    private static final long LISTING_IMAGE_MAX_BYTES = 10L * 1024 * 1024;

    private final ListingImageRepository listingImageRepository;
    private final ListingRepository listingRepository;
    private final StorageService storageService;
    private final RentleProperties props;

    public ListingImageService(ListingImageRepository listingImageRepository,
                               ListingRepository listingRepository,
                               StorageService storageService,
                               RentleProperties props) {
        this.listingImageRepository = listingImageRepository;
        this.listingRepository = listingRepository;
        this.storageService = storageService;
        this.props = props;
    }

    @Transactional
    public List<String> addImages(UUID ownerId, UUID listingId, List<MultipartFile> files) {
        Listing listing = getOwnedListing(ownerId, listingId);
        if (files == null || files.isEmpty()) {
            throw new RentleException("No files provided");
        }
        List<ListingImage> existingImages = listingImageRepository.findByListingIdOrderBySortOrderAsc(listingId);
        if (existingImages.size() + files.size() > props.maxListingImages()) {
            throw new RentleException("A listing can have at most " + props.maxListingImages() + " images");
        }
        for (MultipartFile file : files) {
            ImageValidator.validate(file, LISTING_IMAGE_MAX_BYTES);
        }

        List<String> urls = new ArrayList<>();
        int order = existingImages.stream().mapToInt(ListingImage::getSortOrder).max().orElse(-1) + 1;
        for (MultipartFile file : files) {
            String url = storageService.upload(file, "listings/" + listingId);
            ListingImage image = new ListingImage();
            image.setListing(listing);
            image.setUrl(url);
            image.setSortOrder(order++);
            listingImageRepository.save(image);
            urls.add(url);
        }
        return urls;
    }

    @Transactional
    public void deleteImage(UUID ownerId, UUID listingId, UUID imageId) {
        getOwnedListing(ownerId, listingId);
        ListingImage image = listingImageRepository.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found"));
        if (!image.getListing().getId().equals(listingId)) {
            throw new ResourceNotFoundException("Image not found");
        }
        listingImageRepository.delete(image);
        listingImageRepository.flush();
        List<ListingImage> remaining = listingImageRepository.findByListingIdOrderBySortOrderAsc(listingId);
        for (int index = 0; index < remaining.size(); index++) {
            remaining.get(index).setSortOrder(index);
        }
        listingImageRepository.saveAll(remaining);
    }

    private Listing getOwnedListing(UUID ownerId, UUID listingId) {
        Listing listing = listingRepository.findById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Listing not found"));
        if (!listing.getOwner().getId().equals(ownerId)) {
            throw new UnauthorizedException("Only the listing owner can manage images");
        }
        return listing;
    }
}
