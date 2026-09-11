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
    // ИСПРАВЛЕНО: используем уникальное имя GraffitiPreview вместо TextField
    private GraffitiPreview previewField; 
    private String hintMessage = "Drag & Drop file here\n(or paste URL/path)";

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

        urlField = new TextFieldWidget(this.textRenderer, 20, 60, leftWidth - 30, 20, Text.literal("URL or File Path"));
        urlField.setMaxLength(1000);
        urlField.setChangedListener(this::onUrlChanged);
        urlField.setText(GraffitiConfig.lastImagePath);
        this.addDrawableChild(urlField);
        this.setInitialFocus(urlField);

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Open Drag-and-Drop Window"), btn -> {
            hintMessage = "Drag & Drop directly into this window!";
        }).dimensions(20, 90, leftWidth - 30, 20).build());

        btnSelect = ButtonWidget.builder(Text.literal("Select / Save"), btn -> {
            GraffitiConfig.lastImagePath = urlField.getText().trim();
            GraffitiConfig.save();
            close();
        }).dimensions(20, 120, leftWidth - 30, 20).build();
        this.addDrawableChild(btnSelect);

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

        updateButtons();
    }

    private void onUrlChanged(String text) {
        if (previewField != null) { previewField.cleanup(); previewField = null; }
        if (text.trim().isEmpty()) {
            hintMessage = "Drag & Drop file here\n(or paste URL/path)";
        } else {
            hintMessage = "";
            boolean isGif = text.toLowerCase().endsWith(".gif");
            int previewSize = 150;
            int leftWidth = this.width / 2;
            
            // ИСПРАВЛЕНО: создаем экземпляр GraffitiPreview
            previewField = new GraffitiPreview((leftWidth - previewSize) / 2, 160, previewSize, previewSize, text, isGif, true);
            previewField.keepAspect = true;
        }
        updateButtons();
    }

    private void setBlockSize(int size) {
        GraffitiConfig.defaultBlockSize = size;
        GraffitiConfig.save();
        updateButtons();
    }

    private void setScale(int percent) {
        GraffitiConfig.scalePercent = percent;
        GraffitiConfig.defaultTextureResolution = GraffitiConfig.computeResolution(GraffitiConfig.defaultTextureResolution);
        GraffitiConfig.save();
        updateButtons();
        if (!urlField.getText().trim().isEmpty()) {
            onUrlChanged(urlField.getText());
        }
    }

    private void updateButtons() {
        btn1x1.active = GraffitiConfig.defaultBlockSize != 1;
        btn2x2.active = GraffitiConfig.defaultBlockSize != 2;
        btn3x3.active = GraffitiConfig.defaultBlockSize != 3;
        btn50.active = GraffitiConfig.scalePercent != 50;
        btn100.active = GraffitiConfig.scalePercent != 100;
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
        this.renderBackground(context, mouseX, mouseY, delta);
        
        int leftWidth = this.width / 2;
        context.fill(leftWidth, 0, leftWidth + 1, this.height, 0xFFAAAAAA);
        
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Select Image"), leftWidth / 2, 10, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Settings"), leftWidth + (this.width - leftWidth) / 2, 10, 0xFFFFFF);

        if (previewField != null) {
            previewField.render(context, false);
        } else if (!hintMessage.isEmpty() && !urlField.getText().trim().isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Preview not available"), leftWidth / 2, 180, 0xFF8888);
        }

        context.drawText(this.textRenderer, Text.literal("Scale: " + GraffitiConfig.scalePercent + "%"), leftWidth + 10, 100, 0xFFFFFF, false);
        context.drawText(this.textRenderer, Text.literal("Tex Res: " + GraffitiConfig.defaultTextureResolution), leftWidth + 10, 115, 0xFFFFFF, false);

        urlField.render(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == 256) { close(); return true; }
        return super.keyPressed(input);
    }
}
