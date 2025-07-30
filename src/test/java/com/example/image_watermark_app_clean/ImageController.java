package com.example.image_watermark_app_clean; // ★この行を修正★

import org.springframework.core.io.ByteArrayResource;
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

import javax.imageio.ImageIO; // 追加
import java.awt.*; // 追加
import java.awt.image.BufferedImage; // 追加
import java.io.ByteArrayOutputStream; // 追加
import java.io.IOException; // IOException は既に存在するか確認

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

            // ウォーターマークのテキスト
            String watermarkText = "Sample Watermark"; // ここでウォーターマークのテキストを定義
            Font font = new Font("Arial", Font.BOLD, 40); // フォント、スタイル、サイズを設定
            Color color = new Color(0, 0, 0, 100); // 半透明の黒（RGBA: Aはアルファ値で透明度）

            // 新しい画像を作成し、元の画像をその上に描画
            BufferedImage watermarkedImage = new BufferedImage(
                    originalImage.getWidth(), originalImage.getHeight(), BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = (Graphics2D) watermarkedImage.getGraphics();
            g2d.drawImage(originalImage, 0, 0, null);

            // ウォーターマークテキストの描画設定
            g2d.setFont(font);
            g2d.setColor(color);

            // テキストのサイズと位置を計算 (画像中央に配置)
            FontMetrics fontMetrics = g2d.getFontMetrics();
            int textWidth = fontMetrics.stringWidth(watermarkText);
            int textHeight = fontMetrics.getHeight();

            int x = (originalImage.getWidth() - textWidth) / 2;
            int y = (originalImage.getHeight() - textHeight) / 2 + fontMetrics.getAscent(); // y座標はベースライン

            // ウォーターマークテキストを描画
            g2d.drawString(watermarkText, x, y);
            g2d.dispose(); // Graphicsオブジェクトのリソースを解放

            // 処理された画像をバイト配列に変換
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            // 画像の元のフォーマットを取得 (例: "jpeg", "png", "gif")
            String format = imageFile.getContentType().substring(imageFile.getContentType().indexOf("/") + 1);
            ImageIO.write(watermarkedImage, format, baos);
            byte[] imageBytes = baos.toByteArray();

            // ここで生成された画像をダウンロード可能な形式で返す
            // ウォーターマーク処理後の画像データをセッションに保存するか、
            // リダイレクト先で別のGETエンドポイントに渡す方法を検討する必要がある
            // 今回はシンプルにダウンロードリンクを生成するURLにリダイレクト
            String downloadFileName = "watermarked_" + imageFile.getOriginalFilename();
            // 一時的にダウンロードURLを生成し、フラッシュ属性で渡す
            // (注意: これは単純化された例であり、実際のプロダクション環境ではファイルストレージやセッション管理が必要)
            // 今回は処理後にダウンロードするのではなく、成功メッセージを表示するだけにし、
            // ダウンロード機能は次のステップで明確なダウンロードエンドポイントを作成する
            redirectAttributes.addFlashAttribute("message",
                    "ファイル「" + imageFile.getOriginalFilename() + "」にウォーターマークが適用されました！");
            redirectAttributes.addFlashAttribute("isError", false);
            redirectAttributes.addFlashAttribute("downloadUrl", "/download?filename=" + downloadFileName);
            redirectAttributes.addFlashAttribute("imageBytes", imageBytes); // 画像データをフラッシュ属性で渡す

        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("message", "画像の読み込みまたは書き込み中にエラーが発生しました: " + e.getMessage());
            redirectAttributes.addFlashAttribute("isError", true);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("message", "予期せぬエラーが発生しました: " + e.getMessage());
            redirectAttributes.addFlashAttribute("isError", true);
        }

        return "redirect:/"; // トップページにリダイレクトして結果を表示
    }

    // ダウンロード用のGETエンドポイント (後で実装)
    // ここでは、一時的にセッションに保存された画像データを返すことを想定
    // 実際のプロダクションでは、ファイルストレージから取得するべき
    // @GetMapping("/download")
    // public ResponseEntity<Resource> downloadImage(@RequestParam("filename") String filename,
    //                                                 @SessionAttribute("lastProcessedImage") byte[] imageBytes) {
    //    HttpHeaders headers = new HttpHeaders();
    //    headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
    //    return ResponseEntity.ok()
    //            .headers(headers)
    //            .contentType(MediaType.parseMediaType("image/png")) // または元のMIMEタイプ
    //            .body(new ByteArrayResource(imageBytes));
    // }
}
