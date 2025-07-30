package com.example.image_watermark_app_clean;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.PathVariable;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.awt.AlphaComposite;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Controller
public class ImageController {

    // 処理された単一画像を一時的に保存するためのマップ
    private final Map<String, byte[]> processedImages = new ConcurrentHashMap<>();
    // 処理されたZIPファイルを一時的に保存するためのマップ
    private final Map<String, byte[]> processedZipFiles = new ConcurrentHashMap<>();

    @GetMapping("/")
    public String showUploadForm(Model model) {
        return "upload"; // upload.html を表示
    }

    @PostMapping("/upload")
    public String handleImageUpload(
            @RequestParam("imageFiles") List<MultipartFile> imageFiles,
            @RequestParam(value = "watermarkPosition", defaultValue = "center") String watermarkPosition,
            @RequestParam(value = "watermarkScale", defaultValue = "0.5") float watermarkScale,
            @RequestParam(value = "watermarkColor", defaultValue = "black") String watermarkColor,
            RedirectAttributes redirectAttributes) {

        if (imageFiles.isEmpty() || imageFiles.stream().allMatch(MultipartFile::isEmpty)) {
            redirectAttributes.addFlashAttribute("message", "ファイルを1つ以上選択してください。");
            redirectAttributes.addFlashAttribute("isError", true);
            return "redirect:/";
        }

        try {
            // ウォーターマーク画像のパスを決定
            String watermarkImagePath;
            if ("white".equalsIgnoreCase(watermarkColor)) {
                watermarkImagePath = "static/watermark_white.png";
            } else {
                watermarkImagePath = "static/watermark_black.png";
            }

            Resource watermarkResource = new ClassPathResource(watermarkImagePath);
            BufferedImage watermarkImage;
            try (InputStream is = watermarkResource.getInputStream()) {
                watermarkImage = ImageIO.read(is);
            }

            if (watermarkImage == null) {
                redirectAttributes.addFlashAttribute("message", "ウォーターマーク画像を読み込めませんでした。ファイルが存在しないか、破損している可能性があります。(" + watermarkImagePath + ")");
                redirectAttributes.addFlashAttribute("isError", true);
                return "redirect:/";
            }

            // ★★★ ファイル数による処理の分岐 ★★★
            if (imageFiles.size() == 1) {
                // 単一ファイルの処理
                MultipartFile singleImageFile = imageFiles.get(0);
                if (!singleImageFile.getContentType().startsWith("image/")) {
                    redirectAttributes.addFlashAttribute("message", "画像ファイルのみアップロードできます。");
                    redirectAttributes.addFlashAttribute("isError", true);
                    return "redirect:/";
                }

                BufferedImage originalImage = ImageIO.read(singleImageFile.getInputStream());
                BufferedImage watermarkedImage = new BufferedImage(
                        originalImage.getWidth(), originalImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2d = (Graphics2D) watermarkedImage.getGraphics();
                g2d.drawImage(originalImage, 0, 0, null);

                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f)); // 完全に不透明

                int scaledWatermarkWidth = (int) (watermarkImage.getWidth() * watermarkScale);
                int scaledWatermarkHeight = (int) (watermarkImage.getHeight() * watermarkScale);

                int x = 0;
                int y = 0;

                switch (watermarkPosition) {
                    case "topLeft":
                        x = 0;
                        y = 0;
                        break;
                    case "topRight":
                        x = originalImage.getWidth() - scaledWatermarkWidth;
                        y = 0;
                        break;
                    case "bottomLeft":
                        x = 0;
                        y = originalImage.getHeight() - scaledWatermarkHeight;
                        break;
                    case "bottomRight":
                        x = originalImage.getWidth() - scaledWatermarkWidth;
                        y = originalImage.getHeight() - scaledWatermarkHeight;
                        break;
                    case "center":
                    default:
                        x = (originalImage.getWidth() - scaledWatermarkWidth) / 2;
                        y = (originalImage.getHeight() - scaledWatermarkHeight) / 2;
                        break;
                }

                g2d.drawImage(watermarkImage, x, y, scaledWatermarkWidth, scaledWatermarkHeight, null);
                g2d.dispose();

                ByteArrayOutputStream imageBaos = new ByteArrayOutputStream();
                ImageIO.write(watermarkedImage, "png", imageBaos); // 出力フォーマットをPNGに固定
                byte[] imageBytes = imageBaos.toByteArray();

                // 単一画像をマップに保存
                String imageId = UUID.randomUUID().toString();
                processedImages.put(imageId, imageBytes);

                String originalFileNameWithoutExt = singleImageFile.getOriginalFilename().replaceFirst("[.][^.]+$", "");
                String downloadFileName = "watermarked_" + originalFileNameWithoutExt + ".png";
                String generatedDownloadUrl = "/download/" + imageId + "/" + downloadFileName;

                System.out.println("Single Image Generated ID: " + imageId);
                System.out.println("Single Image Generated Download URL: " + generatedDownloadUrl);

                redirectAttributes.addFlashAttribute("message",
                        "ファイル「" + singleImageFile.getOriginalFilename() + "」にウォーターマークが適用されました！");
                redirectAttributes.addFlashAttribute("isError", false);
                redirectAttributes.addFlashAttribute("downloadUrl", generatedDownloadUrl);

            } else {
                // 複数ファイルの処理 (ZIP化)
                try (ByteArrayOutputStream zipBaos = new ByteArrayOutputStream();
                     ZipOutputStream zos = new ZipOutputStream(zipBaos)) {

                    for (MultipartFile imageFile : imageFiles) {
                        if (imageFile.isEmpty()) {
                            continue;
                        }
                        if (!imageFile.getContentType().startsWith("image/")) {
                            System.err.println("Skipping non-image file: " + imageFile.getOriginalFilename());
                            continue;
                        }

                        BufferedImage originalImage = ImageIO.read(imageFile.getInputStream());
                        BufferedImage watermarkedImage = new BufferedImage(
                                originalImage.getWidth(), originalImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g2d = (Graphics2D) watermarkedImage.getGraphics();
                        g2d.drawImage(originalImage, 0, 0, null);

                        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));

                        int scaledWatermarkWidth = (int) (watermarkImage.getWidth() * watermarkScale);
                        int scaledWatermarkHeight = (int) (watermarkImage.getHeight() * watermarkScale);

                        int x = 0;
                        int y = 0;

                        switch (watermarkPosition) {
                            case "topLeft":
                                x = 0;
                                y = 0;
                                break;
                            case "topRight":
                                x = originalImage.getWidth() - scaledWatermarkWidth;
                                y = 0;
                                break;
                            case "bottomLeft":
                                x = 0;
                                y = originalImage.getHeight() - scaledWatermarkHeight;
                                break;
                            case "bottomRight":
                                x = originalImage.getWidth() - scaledWatermarkWidth;
                                y = originalImage.getHeight() - scaledWatermarkHeight;
                                break;
                            case "center":
                            default:
                                x = (originalImage.getWidth() - scaledWatermarkWidth) / 2;
                                y = (originalImage.getHeight() - scaledWatermarkHeight) / 2;
                                break;
                        }

                        g2d.drawImage(watermarkImage, x, y, scaledWatermarkWidth, scaledWatermarkHeight, null);
                        g2d.dispose();

                        ByteArrayOutputStream imageBaos = new ByteArrayOutputStream();
                        ImageIO.write(watermarkedImage, "png", imageBaos);

                        String originalFileNameWithoutExt = imageFile.getOriginalFilename().replaceFirst("[.][^.]+$", "");
                        String zipEntryFileName = "watermarked_" + originalFileNameWithoutExt + ".png";
                        zos.putNextEntry(new ZipEntry(zipEntryFileName));
                        zos.write(imageBaos.toByteArray());
                        zos.closeEntry();
                    }

                    zos.finish();

                    byte[] zipBytes = zipBaos.toByteArray();

                    // 処理されたZIPファイルをマップに保存
                    String zipId = UUID.randomUUID().toString();
                    processedZipFiles.put(zipId, zipBytes);

                    String generatedDownloadUrl = "/download-zip/" + zipId + "/watermarked_images.zip";

                    System.out.println("ZIP Generated ID: " + zipId);
                    System.out.println("ZIP Generated Download URL: " + generatedDownloadUrl);

                    redirectAttributes.addFlashAttribute("message",
                            imageFiles.size() + "個の画像にウォーターマークが適用され、ZIPファイルが準備されました！");
                    redirectAttributes.addFlashAttribute("isError", false);
                    redirectAttributes.addFlashAttribute("downloadUrl", generatedDownloadUrl);
                }
            }

        } catch (IOException e) {
            System.err.println("IOException during image processing or ZIP creation: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("message", "画像の処理またはZIPファイルの作成中にエラーが発生しました: " + e.getMessage());
            redirectAttributes.addFlashAttribute("isError", true);
        } catch (Exception e) {
            System.err.println("Unexpected Exception during image processing or ZIP creation: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("message", "予期せぬエラーが発生しました: " + e.getMessage());
            redirectAttributes.addFlashAttribute("isError", true);
        }

        return "redirect:/";
    }

    // 単一画像ダウンロード用のGETエンドポイント
    @GetMapping("/download/{imageId}/{filename}")
    public ResponseEntity<Resource> downloadImage(@PathVariable("imageId") String imageId,
                                                  @PathVariable("filename") String filename) {
        System.out.println("Single Image Download request received for Image ID: " + imageId + " and filename: " + filename);

        byte[] imageBytes = processedImages.get(imageId);

        if (imageBytes == null) {
            System.err.println("Single Image data not found for ID: " + imageId);
            return ResponseEntity.notFound().build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("image/png")) // PNGとしてダウンロード
                .body(new ByteArrayResource(imageBytes));
    }

    // ZIPファイルダウンロード用のGETエンドポイント
    @GetMapping("/download-zip/{zipId}/{filename}")
    public ResponseEntity<Resource> downloadZip(@PathVariable("zipId") String zipId,
                                                @PathVariable("filename") String filename) {
        System.out.println("ZIP Download request received for ZIP ID: " + zipId + " and filename: " + filename);

        byte[] zipBytes = processedZipFiles.get(zipId);

        if (zipBytes == null) {
            System.err.println("ZIP data not found for ID: " + zipId);
            return ResponseEntity.notFound().build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("application/zip")) // ZIPファイルとして返す
                .body(new ByteArrayResource(zipBytes));
    }
}
