package ru.yourname.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import ru.yourname.GraffitiConfig;

import java.nio.file.Path;
import java.util.List;

public class GuiGraffitiManager extends Screen {
    private TextFieldWidget urlField;
    private GraffitiPreview previewField;
    private String hintMessage = "Drag & Drop file here";

    private ButtonWidget btn1x1, btn2x2, btn3x3;
    private ButtonWidget btn50, btn100;
    private ButtonWidget btnSelect;

    public GuiGraffitiManager() {
        super(Text.literal("Graffiti Manager"));
    }

    @Override
    protected void init() {
        int leftWidth = this.width / 2;
        int rightX = leftWidth + 10;

        int sizeBtnW = 60, sizeBtnH = 20, sizeY = 80;
        btn1x1 = ButtonWidget.builder(Text.literal("1x1"), b -> setBlockSize(1)).dimensions(rightX, sizeY, sizeBtnW, sizeBtnH).build();
        btn2x2 = ButtonWidget.builder(Text.literal("2x2"), b -> setBlockSize(2)).dimensions(rightX + sizeBtnW + 5, sizeY, sizeBtnW, sizeBtnH).build();
        btn3x3 = ButtonWidget.builder(Text.literal("3x3"), b -> setBlockSize(3)).dimensions(rightX + 2 * (sizeBtnW + 5), sizeY, sizeBtnW, sizeBtnH).build();
        this.addDrawableChild(btn1x1);
        this.addDrawableChild(btn2x2);
        this.addDrawableChild(btn3x3);

        int scaleY = 120;
        btn50 = ButtonWidget.builder(Text.literal("50%"), b -> setScale(50)).dimensions(rightX, scaleY, sizeBtnW, sizeBtnH).build();
        btn100 = ButtonWidget.builder(Text.literal("100%"), b -> setScale(100)).dimensions(rightX + sizeBtnW + 5, scaleY, sizeBtnW, sizeBtnH).build();
        this.addDrawableChild(btn50);
        this.addDrawableChild(btn100);

        urlField = new TextFieldWidget(this.textRenderer, 20, 60, leftWidth - 30, 20, Text.literal("URL or File Path"));
        urlField.setMaxLength(1000);
        urlField.setChangedListener(this::onUrlChanged);
        this.addDrawableChild(urlField);
        this.setInitialFocus(urlField);

        urlField.setText(GraffitiConfig.lastImagePath);

        // СОХРАНЕНИЕ ПРОИСХОДИТ ТОЛЬКО ЗДЕСЬ
        btnSelect = ButtonWidget.builder(Text.literal("Select / Save"), btn -> {
            GraffitiConfig.lastImagePath = urlField.getText().trim();
            GraffitiConfig.save(); 
            close();
        }).dimensions(20, 135, leftWidth - 30, 20).build();
        this.addDrawableChild(btnSelect);
    }

    private void onUrlChanged(String text) {
        if (previewField != null) { previewField.cleanup(); previewField = null; }
        if (text.trim().isEmpty()) {
            hintMessage = "Drag & Drop file here";
        } else {
            hintMessage = "";
            boolean isGif = text.toLowerCase().endsWith(".gif");
            int previewSize = 150;
            int leftWidth = this.width / 2;
            
            previewField = new GraffitiPreview((leftWidth - previewSize) / 2, 160, previewSize, previewSize, text, isGif, true);
            previewField.keepAspect = true;
        }
        updateButtons();
    }

    private void setBlockSize(int size) {
        GraffitiConfig.defaultBlockSize = size;
        updateButtons();
    }

    private void setScale(int percent) {
        GraffitiConfig.scalePercent = percent;
        updateButtons();
        if (!urlField.getText().trim().isEmpty()) {
            onUrlChanged(urlField.getText());
        }
    }

    private void updateButtons() {
        if (btn1x1 != null) btn1x1.active = GraffitiConfig.defaultBlockSize != 1;
        if (btn2x2 != null) btn2x2.active = GraffitiConfig.defaultBlockSize != 2;
        if (btn3x3 != null) btn3x3.active = GraffitiConfig.defaultBlockSize != 3;
        if (btn50 != null) btn50.active = GraffitiConfig.scalePercent != 50;
        if (btn100 != null) btn100.active = GraffitiConfig.scalePercent != 100;
    }

    @Override
    public void onFilesDropped(List<Path> paths) {
        if (paths != null && !paths.isEmpty()) {
            Path path = paths.get(0);
            String fileName = path.getFileName().toString().toLowerCase();
            if (fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg") || fileName.endsWith(".gif") || fileName.endsWith(".webp")) {
                String filePath = path.toAbsolutePath().toString().replace("\\", "/");
                urlField.setText(filePath);
                onUrlChanged(filePath);
            }
        }
    }

    @Override
    public void removed() {
        if (previewField != null) previewField.cleanup();
        super.removed();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x60000000);
        
        int leftWidth = this.width / 2;
        context.fill(leftWidth, 0, leftWidth + 1, this.height, 0xFFAAAAAA);
        
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Select Image"), leftWidth / 2, 10, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Settings"), leftWidth + (this.width - leftWidth) / 2, 10, 0xFFFFFF);

        int dropX = 20;
        int dropY = 85;
        int dropW = leftWidth - 30;
        int dropH = 40;
        
        context.fill(dropX, dropY, dropX + dropW, dropY + dropH, 0x30888888);
        int borderColor = 0xFF55FF55;
        context.fill(dropX, dropY, dropX + dropW, dropY + 1, borderColor);
        context.fill(dropX, dropY + dropH - 1, dropX + dropW, dropY + dropH, borderColor);
        context.fill(dropX, dropY, dropX + 1, dropY + dropH, borderColor);
        context.fill(dropX + dropW - 1, dropY, dropX + dropW, dropY + dropH, borderColor);
        
        // ИСПРАВЛЕНО: 0xFFFFFFFF вместо 0xFFFFFF для полной видимости текста
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(hintMessage), dropX + dropW / 2, dropY + dropH / 2 - 4, 0xFFFFFFFF);

        if (previewField != null) {
            previewField.render(context, false);
        } else if (!hintMessage.equals("Drag & Drop file here") && !urlField.getText().trim().isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Preview not available"), leftWidth / 2, 180, 0xFF8888);
        }

        context.drawText(this.textRenderer, Text.literal("Scale: " + GraffitiConfig.scalePercent + "%"), leftWidth + 10, 100, 0xFFFFFF, false);
        context.drawText(this.textRenderer, Text.literal("Tex Res: " + GraffitiConfig.getTargetResolution()), leftWidth + 10, 115, 0xFFFFFF, false);

        urlField.render(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == 256) { close(); return true; }
        return super.keyPressed(input);
    }
}
