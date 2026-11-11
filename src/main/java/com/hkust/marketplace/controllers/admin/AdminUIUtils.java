package com.hkust.marketplace.controllers.admin;

import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.services.CreateListingService;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;

/**
 * Shared UI rendering utilities and image thumbnail helpers for Admin Portal controllers.
 */
public final class AdminUIUtils {

    public static final int CARD_THUMBNAIL_SIZE = 64;
    public static final int DETAIL_THUMBNAIL_SIZE = 72;
    public static final int FORM_THUMBNAIL_SIZE = 54;

    private AdminUIUtils() {
        // Utility class
    }

    public static Path readablePhoto(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            Path file = Path.of(path);
            return Files.isRegularFile(file) ? file : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    public static StackPane listingThumbnail(Listing listing) {
        StackPane box = new StackPane();
        box.setMinSize(CARD_THUMBNAIL_SIZE, CARD_THUMBNAIL_SIZE);
        box.setMaxSize(CARD_THUMBNAIL_SIZE, CARD_THUMBNAIL_SIZE);
        box.setStyle("-fx-background-color: #f1f5f9; -fx-background-radius: 8; -fx-border-color: #e2e8f0; -fx-border-radius: 8;");
        Path cover = readablePhoto(listing != null ? listing.getThumbnailPath() : null);
        if (cover != null) {
            ImageView view = new ImageView(new Image(cover.toUri().toString(),
                    CARD_THUMBNAIL_SIZE, CARD_THUMBNAIL_SIZE, true, true, true));
            view.setFitWidth(CARD_THUMBNAIL_SIZE);
            view.setFitHeight(CARD_THUMBNAIL_SIZE);
            view.setPreserveRatio(true);
            box.getChildren().add(view);
        } else {
            String emoji = (listing != null && listing.getType() == ListingType.LEND) ? "🤝"
                    : (listing != null && listing.getType() == ListingType.GIVEAWAY) ? "🎁" : "🏷️";
            Label icon = new Label(emoji);
            icon.setStyle("-fx-font-size: 26px;");
            box.getChildren().add(icon);
        }
        return box;
    }

    public static Node photoThumbnailNode(Path photo, int size) {
        StackPane box = new StackPane();
        box.setMinSize(size, size);
        box.setMaxSize(size, size);
        box.setStyle("-fx-background-color: #f1f5f9; -fx-background-radius: 6; -fx-border-color: #cbd5e1; -fx-border-radius: 6;");
        if (photo != null && Files.isRegularFile(photo)) {
            ImageView view = new ImageView(new Image(photo.toUri().toString(), size, size, true, true, true));
            view.setFitWidth(size);
            view.setFitHeight(size);
            view.setPreserveRatio(true);
            box.getChildren().add(view);
        } else {
            Label missing = new Label("📷");
            missing.setStyle("-fx-font-size: 18px; -fx-text-fill: #94a3b8;");
            box.getChildren().add(missing);
        }
        return box;
    }

    public static void renderFormPhotoThumbnails(HBox container, Label countLabel, Button addBtn,
                                                 List<Path> photos, Label emptyLabel) {
        container.getChildren().clear();
        for (int i = 0; i < photos.size(); i++) {
            final int idx = i;
            Path p = photos.get(i);
            StackPane tile = new StackPane();
            tile.setMinSize(FORM_THUMBNAIL_SIZE + 4, FORM_THUMBNAIL_SIZE + 4);
            tile.setMaxSize(FORM_THUMBNAIL_SIZE + 4, FORM_THUMBNAIL_SIZE + 4);
            Node imgNode = photoThumbnailNode(p, FORM_THUMBNAIL_SIZE);
            Button removeBtn = new Button("×");
            removeBtn.setStyle("-fx-font-size: 10px; -fx-background-color: #ef4444; -fx-text-fill: white; -fx-padding: 0 4 0 4; -fx-background-radius: 8;");
            StackPane.setAlignment(removeBtn, Pos.TOP_RIGHT);
            removeBtn.setOnAction(ev -> {
                photos.remove(idx);
                renderFormPhotoThumbnails(container, countLabel, addBtn, photos, emptyLabel);
            });
            tile.getChildren().addAll(imgNode, removeBtn);
            container.getChildren().add(tile);
        }
        if (photos.isEmpty()) {
            container.getChildren().add(emptyLabel);
        }
        countLabel.setText(photos.size() + " / " + CreateListingService.MAX_PHOTOS + " photos");
    }

    public static VBox createMetricCard(String title, String count, String color, String subtitle) {
        VBox card = new VBox(4);
        card.getStyleClass().add("metric-card");
        card.setPadding(new javafx.geometry.Insets(12, 16, 12, 16));
        card.setAlignment(Pos.CENTER_LEFT);
        javafx.scene.layout.HBox.setHgrow(card, javafx.scene.layout.Priority.ALWAYS);

        Label num = new Label(count);
        num.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        Label lbl = new Label(title);
        lbl.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        Label sub = new Label(subtitle);
        sub.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 10px;");

        card.getChildren().addAll(num, lbl, sub);
        return card;
    }
}
