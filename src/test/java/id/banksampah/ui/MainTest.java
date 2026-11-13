package id.banksampah.ui;

import id.banksampah.core.AdminSession;
import id.banksampah.core.BankService;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Window;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.function.BooleanSupplier;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class MainTest {
    @TempDir Path tempDir;

    @AfterEach void cleanUp() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (Window window : Window.getWindows()) window.dispose();
        });
        AdminSession.getInstance().logout();
        System.clearProperty("banksampah.db.path");
    }

    private static <T> T edt(Callable<T> work) throws Exception {
        FutureTask<T> task = new FutureTask<>(work);
        if (SwingUtilities.isEventDispatchThread()) task.run();
        else SwingUtilities.invokeAndWait(task);
        return task.get();
    }

    private static void click(JButton button) throws Exception {
        edt(() -> { assertTrue(button.isEnabled(), button.getText()); button.doClick(); return null; });
    }

    private static void laterClick(JButton button) {
        SwingUtilities.invokeLater(() -> button.doClick());
    }

    private static void await(BooleanSupplier ready) throws Exception {
        long until = System.nanoTime() + 12_000_000_000L;
        while (System.nanoTime() < until) {
            if (edt(ready::getAsBoolean)) return;
            Thread.sleep(40);
        }
        fail("Timed out waiting for Swing state");
    }

    private static <T extends Window> T window(Class<T> type, String title) throws Exception {
        await(() -> Arrays.stream(Window.getWindows()).anyMatch(w -> type.isInstance(w)
                && w.isVisible() && title.equals(w instanceof JDialog d ? d.getTitle() : ((JFrame) w).getTitle())));
        return edt(() -> Arrays.stream(Window.getWindows()).filter(w -> type.isInstance(w) && w.isVisible()
                && title.equals(w instanceof JDialog d ? d.getTitle() : ((JFrame) w).getTitle()))
                .map(type::cast).findFirst().orElseThrow());
    }

    private static <T> T field(Object object, String name, Class<T> type) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return type.cast(field.get(object));
    }

    private static <T extends Component> T child(Container parent, Class<T> type) {
        for (Component item : parent.getComponents()) {
            if (type.isInstance(item)) return type.cast(item);
            if (item instanceof Container container) {
                T found = child(container, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void set(JTextField field, String value) throws Exception {
        edt(() -> { field.setText(value); return null; });
    }

    private static void selectNik(JTable table, String nik) throws Exception {
        edt(() -> {
            for (int i = 0; i < table.getModel().getRowCount(); i++) {
                if (nik.equals(table.getModel().getValueAt(i, 0))) {
                    int view = table.convertRowIndexToView(i);
                    table.setRowSelectionInterval(view, view);
                    return null;
                }
            }
            fail("NIK tidak ditemukan di tabel: " + nik);
            return null;
        });
    }

    private static void confirm(String title, int answer) throws Exception {
        JDialog dialog = window(JDialog.class, title);
        JOptionPane pane = child(dialog, JOptionPane.class);
        assertNotNull(pane);
        edt(() -> { pane.setValue(answer); return null; });
    }

    @Test @Timeout(90)
    void operatorCanUseLoginMasterTransactionsHistoryExportAndLogout() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "UI requires a desktop");
        System.setProperty("banksampah.db.path", tempDir.resolve("test.db").toString());
        Main.main(new String[0]);
        JDialog login = window(JDialog.class, "Masuk | Bank Sampah");
        JTextField username = field(login, "username", JTextField.class);
        JPasswordField password = field(login, "password", JPasswordField.class);
        JLabel message = field(login, "message", JLabel.class);
        JButton submit = field(login, "submit", JButton.class);

        click(submit);
        assertEquals("Isi username dan password.", edt(message::getText));
        set(username, "admin");
        set(password, "salah");
        click(submit);
        await(() -> message.getText().equals("Username atau password salah") && submit.isEnabled());
        set(password, "admin123");
        click(submit);
        JFrame frame = window(JFrame.class, "Bank Sampah | Meja Operator");
        assertFalse(edt(login::isVisible));
        JTabbedPane tabs = field(frame, "tabs", JTabbedPane.class);
        assertEquals(3, edt(tabs::getTabCount));

        JTextField nik = field(frame, "nik", JTextField.class);
        JTextField nama = field(frame, "nama", JTextField.class);
        JTextField hp = field(frame, "hp", JTextField.class);
        JButton save = field(frame, "saveWarga", JButton.class);
        JButton edit = field(frame, "editWarga", JButton.class);
        JButton delete = field(frame, "deleteWarga", JButton.class);
        JButton cancel = field(frame, "cancelEdit", JButton.class);
        JTable warga = field(frame, "wargaTable", JTable.class);
        JLabel wargaStatus = field(frame, "wargaStatus", JLabel.class);
        JTextField searchWarga = field(frame, "searchWarga", JTextField.class);
        await(() -> fieldRowCount(warga) == 0);
        set(nik, "1234567890123456"); set(nama, "Warga Uji"); set(hp, "012");
        click(save);
        assertTrue(edt(() -> wargaStatus.getText().contains("Nomor HP")));
        set(hp, "081234567890");
        click(save);
        await(() -> fieldRowCount(warga) == 1 && save.isEnabled());
        selectNik(warga, "1234567890123456");
        click(edit);
        assertEquals("Simpan perubahan", edt(save::getText));
        set(nama, "Warga Uji Baru");
        click(save);
        await(() -> fieldRowCount(warga) == 1 && "Warga Uji Baru".equals(warga.getModel().getValueAt(0, 1)));
        selectNik(warga, "1234567890123456");
        click(edit);
        click(cancel);
        assertEquals("Tambah warga", edt(save::getText));
        set(nik, "2222222222222222"); set(nama, "Warga Hapus"); set(hp, "081234567891");
        click(save);
        await(() -> fieldRowCount(warga) == 2);
        set(searchWarga, "2222222222222222");
        await(() -> fieldRowCount(warga) == 1);
        selectNik(warga, "2222222222222222");
        laterClick(delete);
        confirm("Konfirmasi hapus", JOptionPane.YES_OPTION);
        await(() -> fieldRowCount(warga) == 0);
        set(searchWarga, "");
        await(() -> fieldRowCount(warga) == 1);

        edt(() -> { tabs.setSelectedIndex(1); return null; });
        JComboBox<?> setorWarga = field(frame, "setorWarga", JComboBox.class);
        JComboBox<?> kategori = field(frame, "kategori", JComboBox.class);
        JTextField berat = field(frame, "berat", JTextField.class);
        JButton setor = field(frame, "setorButton", JButton.class);
        JLabel preview = field(frame, "previewStatus", JLabel.class);
        JLabel setorStatus = field(frame, "setorStatus", JLabel.class);
        await(() -> setorWarga.getItemCount() == 1 && kategori.getItemCount() == 3
                && !fieldUnchecked(frame, "optionsLoading", Boolean.class)
                && !fieldUnchecked(frame, "kategoriLoading", Boolean.class));
        edt(() -> { setorWarga.setSelectedIndex(0); kategori.setSelectedIndex(2); return null; });
        set(berat, "4");
        await(() -> setor.isEnabled() && preview.getText().contains("Poin setoran"));
        click(setor);
        await(() -> setorStatus.getText().contains("berhasil"));
        await(() -> fieldRowCount(warga) == 1 && "1.200".equals(warga.getModel().getValueAt(0, 3)));

        JTable tarifTable = field(frame, "tarifTable", JTable.class);
        JButton ubahTarif = field(frame, "ubahTarif", JButton.class);
        JLabel tarifStatus = field(frame, "tarifStatus", JLabel.class);
        selectNik(tarifTable, "PLASTIK");
        laterClick(ubahTarif);
        JDialog invalidDialog = window(JDialog.class, "Ubah tarif kategori");
        set(child(invalidDialog, JTextField.class), "0");
        edt(() -> { child(invalidDialog, JOptionPane.class).setValue(JOptionPane.OK_OPTION); return null; });
        await(() -> tarifStatus.getText().contains("lebih dari nol"));
        laterClick(ubahTarif);
        JDialog confirmedDialog = window(JDialog.class, "Ubah tarif kategori");
        set(child(confirmedDialog, JTextField.class), "450");
        edt(() -> { child(confirmedDialog, JOptionPane.class).setValue(JOptionPane.OK_OPTION); return null; });
        await(() -> tarifStatus.getText().contains("diperbarui")
                && !fieldUnchecked(frame, "kategoriLoading", Boolean.class)
                && ((BankService.Kategori) kategori.getSelectedItem()).tarif() == 450);
        set(berat, "1");
        await(() -> setor.isEnabled() && preview.getText().contains("450.00"));
        click(setor);
        await(() -> fieldRowCount(warga) == 1 && "1.650".equals(warga.getModel().getValueAt(0, 3)));

        edt(() -> { tabs.setSelectedIndex(2); return null; });
        JComboBox<?> tarikWarga = field(frame, "tarikWarga", JComboBox.class);
        JTextField poin = field(frame, "poin", JTextField.class);
        JComboBox<?> metode = field(frame, "metode", JComboBox.class);
        JLabel tarikPreview = field(frame, "tarikPreview", JLabel.class);
        JButton tarik = field(frame, "tarikButton", JButton.class);
        JLabel tarikStatus = field(frame, "tarikStatus", JLabel.class);
        await(() -> tarikWarga.getItemCount() == 1 && fieldRowCount(fieldUnchecked(frame, "historyTable", JTable.class)) == 2);
        edt(() -> { tarikWarga.setSelectedIndex(0); return null; });
        set(poin, "900");
        assertFalse(edt(tarik::isEnabled));
        set(poin, "1000");
        edt(() -> { metode.setSelectedIndex(1); return null; });
        await(() -> tarik.isEnabled() && tarikPreview.getText().contains("Rp 10.000"));
        laterClick(tarik);
        confirm("Konfirmasi penarikan", JOptionPane.YES_OPTION);
        await(() -> tarikStatus.getText().contains("berhasil"));
        JTable history = field(frame, "historyTable", JTable.class);
        await(() -> fieldRowCount(history) == 3);
        assertTrue(edt(() -> {
            boolean oldRate = false, newRate = false;
            for (int row = 0; row < history.getModel().getRowCount(); row++) {
                String detail = String.valueOf(history.getModel().getValueAt(row, 5));
                oldRate |= detail.contains("300 poin/kg");
                newRate |= detail.contains("450 poin/kg");
            }
            return oldRate && newRate;
        }));
        JComboBox<?> tipe = field(frame, "tipe", JComboBox.class);
        JTextField searchNik = field(frame, "searchNik", JTextField.class);
        edt(() -> { tipe.setSelectedIndex(1); return null; });
        await(() -> fieldRowCount(history) == 2);
        set(searchNik, "9999999999999999");
        await(() -> fieldRowCount(history) == 0);
        set(searchNik, "1234567890123456");
        await(() -> fieldRowCount(history) == 2);
        edt(() -> { tipe.setSelectedIndex(2); return null; });
        await(() -> fieldRowCount(history) == 1 && "E-WALLET".equals(history.getModel().getValueAt(0, 5)));
        edt(() -> { tipe.setSelectedIndex(0); return null; });
        await(() -> fieldRowCount(history) == 3);

        JButton export = field(frame, "exportButton", JButton.class);
        JLabel exportStatus = field(frame, "exportStatus", JLabel.class);
        laterClick(export);
        JDialog saveDialog = window(JDialog.class, "Simpan laporan PDF");
        JFileChooser chooser = child(saveDialog, JFileChooser.class);
        assertNotNull(chooser);
        Path report = tempDir.resolve("laporan.pdf");
        edt(() -> { chooser.setSelectedFile(report.toFile()); chooser.approveSelection(); return null; });
        await(() -> Files.exists(report) && exportStatus.getText().startsWith("PDF disimpan"));
        assertTrue(Files.size(report) > 500);

        edt(() -> { tabs.setSelectedIndex(0); return null; });
        await(() -> fieldRowCount(warga) == 1);
        selectNik(warga, "1234567890123456");
        await(delete::isEnabled);
        laterClick(delete);
        confirm("Konfirmasi hapus", JOptionPane.YES_OPTION);
        await(() -> wargaStatus.getText().equals("Warga tidak dapat dihapus karena memiliki riwayat transaksi"));
        assertEquals(1, edt(() -> fieldRowCount(warga)));
        assertEquals(3, edt(() -> fieldRowCount(history)));

        JButton logout = field(frame, "logout", JButton.class);
        laterClick(logout);
        confirm("Konfirmasi keluar", JOptionPane.YES_OPTION);
        login = window(JDialog.class, "Masuk | Bank Sampah");
        assertNull(AdminSession.getInstance().getAdmin());
        set(field(login, "username", JTextField.class), "admin");
        set(field(login, "password", JPasswordField.class), "admin123");
        click(field(login, "submit", JButton.class));
        window(JFrame.class, "Bank Sampah | Meja Operator");
    }

    private static int fieldRowCount(JTable table) { return table.getModel().getRowCount(); }

    private static <T> T fieldUnchecked(Object owner, String name, Class<T> type) {
        try { return field(owner, name, type); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
