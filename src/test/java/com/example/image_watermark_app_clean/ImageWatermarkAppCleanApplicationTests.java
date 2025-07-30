package com.example.image_watermark_app_clean; // あなたのテストクラスの実際のパッケージ名

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// ★★★ この行を正確に修正します ★★★
// あなたのメインアプリケーションクラスの完全修飾名を指定
@SpringBootTest(classes = com.example.image_watermark_app_clean.ImageWatermarkAppCleanApplication.class)
class ImageWatermarkAppCleanApplicationTests {

    @Test
    void contextLoads() {
        // テストの内容は空で問題ありません
    }

}