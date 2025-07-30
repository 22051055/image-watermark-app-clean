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

@Controller
public class ImageController {

    // 処理された画像を一時的に保存するためのマップ
    // (注意: これはシンプルなデモ用であり、プロダクション環境ではファイルシステムやクラウドストレージを使用すべき)
    private final Map<String, byte[]> processedImages = new ConcurrentHashMap<>();

    @GetMapping("/")
    public String showUploadForm(Model model) {
        return "upload"; // upload.html を表示
    }

    @PostMapping("/upload")
    public String handleImageUpload(
            @RequestParam("imageFile") MultipartFile imageFile,
            @RequestParam(value = "watermarkPosition", defaultValue = "center") String watermarkPosition, // ウォーターマーク位置
            @RequestParam(value = "watermarkScale", defaultValue = "0.5") float watermarkScale, // ウォーターマークサイズ (0.0 - 1.0)
            @RequestParam(value = "watermarkColor", defaultValue = "black") String watermarkColor, // 追加: ウォーターマーク色 (white/black)
            RedirectAttributes redirectAttributes) {

        if (imageFile.isEmpty()) {
            redirectAttributes.addFlashAttribute("message", "ファイルを選択してください。");
            redirectAttributes.addFlashAttribute("isError", true);
            return "redirect:/";
        }

        if (!imageFile.getContentType().startsWith("image/")) {
            redirectAttributes.addFlashAttribute("message", "画像ファイルのみアップロードできます。");
            redirectAttributes.addFlashAttribute("isError", true);
            return "redirect:/";
        }

        try {
            BufferedImage originalImage = ImageIO.read(imageFile.getInputStream());

            // ★★★ ウォーターマーク画像を色選択に基づいて読み込む部分 ★★★
            String watermarkImagePath;
            if ("white".equalsIgnoreCase(watermarkColor)) {
                watermarkImagePath = "static/watermark_white.png";
            } else {
                watermarkImagePath = "static/watermark_black.png"; // デフォルトは黒
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

            BufferedImage watermarkedImage = new BufferedImage(
                    originalImage.getWidth(), originalImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = (Graphics2D) watermarkedImage.getGraphics();
            g2d.drawImage(originalImage, 0, 0, null);

            // ウォーターマークの透明度を設定 (0.0f - 1.0f)
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f)); // 完全に不透明

            // ウォーターマークのサイズを調整
            int scaledWatermarkWidth = (int) (watermarkImage.getWidth() * watermarkScale);
            int scaledWatermarkHeight = (int) (watermarkImage.getHeight() * watermarkScale);

            // ウォーターマークの描画位置を計算
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

            // 調整されたサイズと位置でウォーターマーク画像を描画
            g2d.drawImage(watermarkImage, x, y, scaledWatermarkWidth, scaledWatermarkHeight, null);
            g2d.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(watermarkedImage, "png", baos); // 出力フォーマットをPNGに固定
            byte[] imageBytes = baos.toByteArray();

            String imageId = UUID.randomUUID().toString();
            processedImages.put(imageId, imageBytes);

            String originalFileNameWithoutExt = imageFile.getOriginalFilename().replaceFirst("[.][^.]+$", "");
            String downloadFileName = "watermarked_" + originalFileNameWithoutExt + ".png";

            String generatedDownloadUrl = "/download/" + imageId + "/" + downloadFileName;

            System.out.println("Generated Image ID: " + imageId);
            System.out.println("Generated Download File Name: " + downloadFileName);
            System.out.println("Generated Download URL: " + generatedDownloadUrl);

            redirectAttributes.addFlashAttribute("message",
                    "ファイル「" + imageFile.getOriginalFilename() + "」にウォーターマークが適用されました！");
            redirectAttributes.addFlashAttribute("isError", false);
            redirectAttributes.addFlashAttribute("downloadUrl", generatedDownloadUrl);

        } catch (IOException e) {
            System.err.println("IOException during image processing: " + e.getMessage());
            redirectAttributes.addFlashAttribute("message", "画像の読み込みまたは書き込み中にエラーが発生しました: " + e.getMessage());
            redirectAttributes.addFlashAttribute("isError", true);
        } catch (Exception e) {
            System.err.println("Unexpected Exception during image processing: " + e.getMessage());
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("message", "予期せぬエラーが発生しました: " + e.getMessage());
            redirectAttributes.addFlashAttribute("isError", true);
        }

        return "redirect:/";
    }

    @GetMapping("/download/{imageId}/{filename}")
    public ResponseEntity<Resource> downloadImage(@PathVariable("imageId") String imageId,
                                                  @PathVariable("filename") String filename) {
        System.out.println("Download request received for Image ID: " + imageId + " and filename: " + filename);

        byte[] imageBytes = processedImages.get(imageId);

        if (imageBytes == null) {
            System.err.println("Image data not found for ID: " + imageId);
            return ResponseEntity.notFound().build();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("image/png"))
                .body(new ByteArrayResource(imageBytes));
    }
}
