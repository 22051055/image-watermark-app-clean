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
import org.springframework.web.bind.annotation.PathVariable; // 追加

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
    public String handleImageUpload(@RequestParam("imageFile") MultipartFile imageFile,
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

            Resource watermarkResource = new ClassPathResource("static/watermark.png");
            BufferedImage watermarkImage;
            try (InputStream is = watermarkResource.getInputStream()) {
                watermarkImage = ImageIO.read(is);
            }

            if (watermarkImage == null) {
                redirectAttributes.addFlashAttribute("message", "ウォーターマーク画像を読み込めませんでした。ファイルが存在しないか、破損している可能性があります。");
                redirectAttributes.addFlashAttribute("isError", true);
                return "redirect:/";
            }

            BufferedImage watermarkedImage = new BufferedImage(
                    originalImage.getWidth(), originalImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = (Graphics2D) watermarkedImage.getGraphics();
            g2d.drawImage(originalImage, 0, 0, null);

            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));

            int watermarkWidth = watermarkImage.getWidth();
            int watermarkHeight = watermarkImage.getHeight();

            int x = (originalImage.getWidth() - watermarkWidth) / 2;
            int y = (originalImage.getHeight() - watermarkHeight) / 2;

            g2d.drawImage(watermarkImage, x, y, null);
            g2d.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(watermarkedImage, "png", baos); // 出力フォーマットをPNGに固定
            byte[] imageBytes = baos.toByteArray();

            String imageId = UUID.randomUUID().toString();
            processedImages.put(imageId, imageBytes);

            String originalFileNameWithoutExt = imageFile.getOriginalFilename().replaceFirst("[.][^.]+$", "");
            String downloadFileName = "watermarked_" + originalFileNameWithoutExt + ".png";

            String generatedDownloadUrl = "/download/" + imageId + "/" + downloadFileName; // 生成されるURL

            // ★★★ ここにデバッグログを追加 ★★★
            System.out.println("Generated Image ID: " + imageId);
            System.out.println("Generated Download File Name: " + downloadFileName);
            System.out.println("Generated Download URL: " + generatedDownloadUrl);


            redirectAttributes.addFlashAttribute("message",
                    "ファイル「" + imageFile.getOriginalFilename() + "」にウォーターマークが適用されました！");
            redirectAttributes.addFlashAttribute("isError", false);
            redirectAttributes.addFlashAttribute("downloadUrl", generatedDownloadUrl); // 生成されたURLを渡す


        } catch (IOException e) {
            System.err.println("IOException during image processing: " + e.getMessage()); // エラーログ
            redirectAttributes.addFlashAttribute("message", "画像の読み込みまたは書き込み中にエラーが発生しました: " + e.getMessage());
            redirectAttributes.addFlashAttribute("isError", true);
        } catch (Exception e) {
            System.err.println("Unexpected Exception during image processing: " + e.getMessage()); // エラーログ
            e.printStackTrace(); // スタックトレースも出力
            redirectAttributes.addFlashAttribute("message", "予期せぬエラーが発生しました: " + e.getMessage());
            redirectAttributes.addFlashAttribute("isError", true);
        }

        return "redirect:/";
    }

    @GetMapping("/download/{imageId}/{filename}")
    public ResponseEntity<Resource> downloadImage(@PathVariable("imageId") String imageId,
                                                  @PathVariable("filename") String filename) {
        System.out.println("Download request received for Image ID: " + imageId + " and filename: " + filename); // デバッグログ

        byte[] imageBytes = processedImages.get(imageId);

        if (imageBytes == null) {
            System.err.println("Image data not found for ID: " + imageId); // エラーログ
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
