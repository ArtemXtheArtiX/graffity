package ru.yourname.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Direction;
import ru.yourname.Graffiti;
import ru.yourname.GraffitiConfig;

import java.nio.file.Path;
import java.util.List;

public class GuiGraffitiManager extends Screen {
	private TextFieldWidget urlField;
	private TextField previewField;
	private String hintMessage = "Drag & Drop file here!\n(or paste URL/path)";

	private ButtonWidget btn1x1, btn2x2, btn3x3;
	private ButtonWidget btn50, btn100;

	public GuiGraffitiManager() {
		super(Text.literal("Graffiti Manager"));
	}

	@Override
	protected void init() {
		urlField = new TextFieldWidget(this.textRenderer, this.width / 2 - 110, this.height / 2 - 40, 220, 20, Text.literal("URL or File Path"));
		urlField.setMaxLength(1000);
		urlField.setChangedListener(this::onUrlChanged);
		urlField.setText(GraffitiConfig.lastImagePath);
		this.addDrawableChild(urlField);
		this.setInitialFocus(urlField);

		int btnW = 60, btnH = 20, btnY = this.height / 2;
		btn1x1 = ButtonWidget.builder(Text.literal("1x1"), b -> setBlockSize(1)).dimensions(this.width / 2 + 20, btnY, btnW, btnH).build();
		btn2x2 = ButtonWidget.builder(Text.literal("2x2"), b -> setBlockSize(2)).dimensions(this.width / 2 + 20 + btnW + 5, btnY, btnW, btnH).build();
		btn3x3 = ButtonWidget.builder(Text.literal("3x3"), b -> setBlockSize(3)).dimensions(this.width / 2 + 20 + (btnW + 5) * 2, btnY, btnW, btnH).build();
		this.addDrawableChild(btn1x1);
		this.addDrawableChild(btn2x2);
		this.addDrawableChild(btn3x3);

		int scaleY = btnY + 30;
		btn50 = ButtonWidget.builder(Text.literal("50%"), b -> setScale(50)).dimensions(this.width / 2 + 20, scaleY, btnW, btnH).build();
		btn100 = ButtonWidget.builder(Text.literal("100%"), b -> setScale(100)).dimensions(this.width / 2 + 20 + btnW + 5, scaleY, btnW, btnH).build();
		this.addDrawableChild(btn50);
		this.addDrawableChild(btn100);

		ButtonWidget placeBtn = ButtonWidget.builder(Text.literal("Place on Looked Block"), b -> placeGraffiti()).dimensions(this.width / 2 - 110, this.height / 2 + 40, 220, 20).build();
		this.addDrawableChild(placeBtn);

		updateButtons();
	}

	private void onUrlChanged(String text) {
		if (previewField != null) { previewField.cleanup(); previewField = null; }
		if (text.trim().isEmpty()) {
			hintMessage = "Drag & Drop file here!\n(or paste URL/path)";
		} else {
			hintMessage = "";
			boolean isGif = text.toLowerCase().endsWith(".gif");
			int size = 80;
			previewField = new TextField(this.width / 2 - 110 + (220 - size) / 2, this.height / 2 - 110, size, size, text, isGif);
			previewField.keepAspect = true;
		}
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
	}

	private void updateButtons() {
		btn1x1.active = GraffitiConfig.defaultBlockSize != 1;
		btn2x2.active = GraffitiConfig.defaultBlockSize != 2;
		btn3x3.active = GraffitiConfig.defaultBlockSize != 3;
		btn50.active = GraffitiConfig.scalePercent != 50;
		btn100.active = GraffitiConfig.scalePercent != 100;
	}

	private void placeGraffiti() {
		String source = urlField.getText().trim();
		if (source.isEmpty()) return;

		PlayerEntity player = client.player;
		if (player == null) return;

		BlockHitResult hit = (BlockHitResult) player.raycast(5.0, 0.0f, false);
		Direction side = hit.getSide();
		
		Graffiti g = new Graffiti(hit.getBlockPos(), side, source, GraffitiConfig.defaultBlockSize, GraffitiConfig.defaultTextureResolution, 0, player.getUuid());
		GraffitiManager.addGraffiti(g.uuid, g);
		
		GraffitiConfig.lastImagePath = source;
		GraffitiConfig.save();
		close();
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
			} else {
				hintMessage = "§cUnsupported file type!\n§7Only images and GIFs";
				if (previewField != null) { previewField.cleanup(); previewField = null; }
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
		super.render(context, mouseX, mouseY, delta);

		context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Select Graffiti Image"), this.width / 2, this.height / 2 - 90, 0xFFFFFFFF);

		int dropX = this.width / 2 - 110, dropY = this.height / 2 - 110, dropW = 220, dropH = 60;
		context.fill(dropX, dropY, dropX + dropW, dropY + dropH, 0x30888888);
		int borderColor = 0xFF55FF55;
		context.fill(dropX, dropY, dropX + dropW, dropY + 2, borderColor);
		context.fill(dropX, dropY + dropH - 2, dropX + dropW, dropY + dropH, borderColor);
		context.fill(dropX, dropY, dropX + 2, dropY + dropH, borderColor);
		context.fill(dropX + dropW - 2, dropY, dropX + dropW, dropY + dropH, borderColor);

		if (previewField != null) {
			previewField.render(context, false);
		} else {
			String[] lines = hintMessage.split("\n");
			int textY = dropY + (dropH / 2) - ((lines.length * 10) / 2) + 2;
			for (String line : lines) {
				context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(line), this.width / 2, textY, 0xFFFFFFFF);
				textY += 12;
			}
		}

		urlField.render(context, mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(KeyInput input) {
		if (input.key() == 256) { close(); return true; }
		return super.keyPressed(input);
	}
}
