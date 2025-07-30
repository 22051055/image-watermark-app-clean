package com.example.image_watermark_app_clean; // パッケージ名が正しいことを確認

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource; // 追加
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

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream; // 追加
import java.awt.AlphaComposite; // 追加

@Controller
public class ImageController {

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
            // アップロードされた画像を読み込む
            BufferedImage originalImage = ImageIO.read(imageFile.getInputStream());

            // ★★★ ウォーターマーク画像を読み込む部分 ★★★
            // src/main/resources/static/watermark.png から画像を読み込む
            Resource watermarkResource = new ClassPathResource("static/watermark.png");
            BufferedImage watermarkImage;
            try (InputStream is = watermarkResource.getInputStream()) {
                watermarkImage = ImageIO.read(is);
            }

            if (watermarkImage == null) {
                // ウォーターマーク画像が読み込めなかった場合のエラーハンドリング
                redirectAttributes.addFlashAttribute("message", "ウォーターマーク画像を読み込めませんでした。ファイルが存在しないか、破損している可能性があります。");
                redirectAttributes.addFlashAttribute("isError", true);
                return "redirect:/";
            }

            // 新しい画像を作成し、元の画像をその上に描画
            BufferedImage watermarkedImage = new BufferedImage(
                    originalImage.getWidth(), originalImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = (Graphics2D) watermarkedImage.getGraphics();
            g2d.drawImage(originalImage, 0, 0, null);

            // ★★★ ウォーターマーク画像の描画設定と描画 ★★★
            // 半透明にするための設定 (例: 50%の透明度)
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));

            // ウォーターマーク画像の幅と高さを取得
            int watermarkWidth = watermarkImage.getWidth();
            int watermarkHeight = watermarkImage.getHeight();

            // 描画位置を計算（例: 画像の中央に配置）
            int x = (originalImage.getWidth() - watermarkWidth) / 2;
            int y = (originalImage.getHeight() - watermarkHeight) / 2;

            // ウォーターマーク画像を描画
            g2d.drawImage(watermarkImage, x, y, null);
            g2d.dispose(); // Graphicsオブジェクトのリソースを解放

            // 処理された画像をバイト配列に変換
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // 画像の元のフォーマットを取得 (例: "jpeg", "png", "gif")
            String format = imageFile.getContentType().substring(imageFile.getContentType().indexOf("/") + 1);
            // PNG形式のウォーターマーク画像を重ねた場合、出力もPNGが推奨されることが多い
            // ただし、元の画像フォーマットを維持したい場合は 'format' を使用
            ImageIO.write(watermarkedImage, "png", baos); // 出力フォーマットをPNGに固定
            byte[] imageBytes = baos.toByteArray();

            // ここで生成された画像をダウンロード可能な形式で返す
            String downloadFileName = "watermarked_" + imageFile.getOriginalFilename().replaceFirst("[.][^.]+$", "") + ".png"; // ファイル名を.pngに統一

            redirectAttributes.addFlashAttribute("message",
                    "ファイル「" + imageFile.getOriginalFilename() + "」にウォーターマークが適用されました！");
            redirectAttributes.addFlashAttribute("isError", false);
            // 画像データをセッションに保存し、ダウンロードエンドポイントで利用できるようにする
            // (注意: これは一時的な方法であり、プロダクション環境ではファイルストレージを使用すべき)
            redirectAttributes.addFlashAttribute("lastProcessedImage", imageBytes);
            redirectAttributes.addFlashAttribute("downloadFileName", downloadFileName);


        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("message", "画像の読み込みまたは書き込み中にエラーが発生しました: " + e.getMessage());
            redirectAttributes.addFlashAttribute("isError", true);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("message", "予期せぬエラーが発生しました: " + e.getMessage());
            redirectAttributes.addFlashAttribute("isError", true);
        }

        return "redirect:/"; // トップページにリダイレクトして結果を表示
    }

    // ★★★ ダウンロード用のGETエンドポイントを追加 ★★★
    // @SessionAttribute を使用するために、pom.xml に spring-boot-starter-web の依存関係があることを確認してください。
    // また、セッション管理を有効にするための設定（通常はデフォルトで有効）も必要です。
    @GetMapping("/download")
    public ResponseEntity<Resource> downloadImage(@RequestParam("filename") String filename,
                                                  @RequestParam("imageBytes") byte[] imageBytes) {
        // 注: @SessionAttribute はSpring Boot 3.xでは直接使用できません。
        // ここでは、Flash Attributesを介して渡されたバイト配列を直接利用するように変更します。
        // ただし、Flash Attributesはリダイレクト後に一度しか利用できないため、
        // 実際にはセッションに保存するか、一時ファイルに保存してダウンロードさせるのが一般的です。
        // 今回はシンプルに、リダイレクト後のページでダウンロードをトリガーする形にします。
        // この @GetMapping("/download") エンドポイントは、後で別の方法で画像データを取得するように変更します。
        // 現時点では、このエンドポイントは直接呼び出されません。

        // ダウンロード可能なファイルとしてレスポンスを構築
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");

        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType("image/png")) // PNGとしてダウンロード
                .body(new ByteArrayResource(imageBytes));
    }
}
