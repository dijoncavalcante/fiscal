import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Gera o ícone do FISCAL: quadrado azul arredondado, documento branco com canto dobrado e selo verde de "ok".
 *
 * Uso (na raiz do repositório): java tools/IconGenerator.java
 * Saída: src/main/resources/icons/fiscal-<tamanho>.png (janela e barra de tarefas) e packaging/fiscal.ico (instalador).
 */
public class IconGenerator {
    private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

    public static void main(String[] args) throws IOException {
        Path pngDir = Path.of("src/main/resources/icons");
        Path icoFile = Path.of("packaging/fiscal.ico");
        Files.createDirectories(pngDir);
        Files.createDirectories(icoFile.getParent());

        List<byte[]> pngs = new ArrayList<>();
        for (int size : SIZES) {
            byte[] png = toPng(draw(size));
            Files.write(pngDir.resolve("fiscal-" + size + ".png"), png);
            pngs.add(png);
        }
        Files.write(icoFile, toIco(pngs));
        System.out.println("Ícones gerados em " + pngDir.toAbsolutePath() + " e " + icoFile.toAbsolutePath());
    }

    /** Desenha numa grade de 256 e escala; nos tamanhos pequenos tira as linhas do texto para não virar borrão. */
    private static BufferedImage draw(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.scale(size / 256.0, size / 256.0);
        boolean small = size <= 24;

        // Fundo
        g.setPaint(new GradientPaint(0, 0, new Color(0x2F78D0), 0, 256, new Color(0x1A4F94)));
        g.fill(new RoundRectangle2D.Double(8, 8, 240, 240, 56, 56));

        // Documento com canto dobrado
        double left = 62, top = 36, right = 178, bottom = 214, fold = 40;
        Path2D page = new Path2D.Double();
        page.moveTo(left + 10, top);
        page.lineTo(right - fold, top);
        page.lineTo(right, top + fold);
        page.lineTo(right, bottom - 10);
        page.quadTo(right, bottom, right - 10, bottom);
        page.lineTo(left + 10, bottom);
        page.quadTo(left, bottom, left, bottom - 10);
        page.lineTo(left, top + 10);
        page.quadTo(left, top, left + 10, top);
        page.closePath();
        g.setColor(Color.WHITE);
        g.fill(page);

        Path2D corner = new Path2D.Double();
        corner.moveTo(right - fold, top);
        corner.lineTo(right - fold, top + fold - 8);
        corner.quadTo(right - fold, top + fold, right - fold + 8, top + fold);
        corner.lineTo(right, top + fold);
        corner.closePath();
        g.setColor(new Color(0xC9DAF0));
        g.fill(corner);

        // Linhas de texto
        if (!small) {
            g.setColor(new Color(0x9DB8DC));
            g.setStroke(new BasicStroke(12, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(84, 104, 156, 104);
            g.drawLine(84, 132, 156, 132);
            g.drawLine(84, 160, 124, 160);
        }

        // Selo verde com "ok"
        double cx = 180, cy = 182, r = small ? 50 : 44;
        g.setColor(new Color(0x1A4F94));
        g.fill(new Ellipse2D.Double(cx - r - 8, cy - r - 8, (r + 8) * 2, (r + 8) * 2));
        g.setColor(new Color(0x2E9E55));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
        Path2D check = new Path2D.Double();
        check.moveTo(cx - r * 0.45, cy + r * 0.02);
        check.lineTo(cx - r * 0.1, cy + r * 0.36);
        check.lineTo(cx + r * 0.48, cy - r * 0.3);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke((float) (r * 0.26), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(check);

        g.dispose();
        return image;
    }

    private static byte[] toPng(BufferedImage image) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    /** Arquivo .ico com as imagens em PNG (aceito pelo Windows Vista em diante e pelo jpackage). */
    private static byte[] toIco(List<byte[]> pngs) {
        int headerSize = 6 + 16 * pngs.size();
        int total = headerSize + pngs.stream().mapToInt(p -> p.length).sum();
        ByteBuffer buffer = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) 0).putShort((short) 1).putShort((short) pngs.size());
        int offset = headerSize;
        for (int i = 0; i < pngs.size(); i++) {
            int size = SIZES[i];
            buffer.put((byte) (size >= 256 ? 0 : size)).put((byte) (size >= 256 ? 0 : size));
            buffer.put((byte) 0).put((byte) 0);
            buffer.putShort((short) 1).putShort((short) 32);
            buffer.putInt(pngs.get(i).length).putInt(offset);
            offset += pngs.get(i).length;
        }
        pngs.forEach(buffer::put);
        return buffer.array();
    }
}
