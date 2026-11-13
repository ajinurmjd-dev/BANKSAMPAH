package id.banksampah.ui;

import com.formdev.flatlaf.FlatLightLaf;
import id.banksampah.core.AdminSession;
import id.banksampah.core.BankService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public final class Main {
    private static final Color INK = new Color(32, 48, 42);
    private static final Color MUTED = new Color(68, 84, 76);
    private static final Color GREEN = new Color(30, 91, 66);
    private static final Color ERROR = new Color(154, 40, 36);
    private static final Color BACKGROUND = new Color(244, 247, 244);
    private static final Color BORDER = new Color(207, 218, 209);
    private static final Locale ID = new Locale.Builder().setLanguage("id").setRegion("ID").build();

    private Main() { }

    public static void main(String[] args) {
        FlatLightLaf.setup();
        UIManager.put("Button.arc", 8);
        UIManager.put("Component.arc", 8);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("Component.focusWidth", 2);
        UIManager.put("Component.focusColor", GREEN);
        UIManager.put("Table.rowHeight", 34);
        SwingUtilities.invokeLater(Main::showLogin);
    }

    private static void showLogin() {
        Login dialog = new Login();
        dialog.setVisible(true);
        if (dialog.admin == null) return;
        OperatorFrame frame = new OperatorFrame(dialog.service, dialog.admin);
        frame.setVisible(true);
    }

    private static JLabel label(String text) {
        JLabel result = new JLabel(text);
        result.setForeground(INK);
        return result;
    }

    private static JLabel status() {
        JLabel result = new JLabel(" ");
        result.setForeground(MUTED);
        return result;
    }

    private static void status(JLabel label, String message, boolean error) {
        label.setForeground(error ? ERROR : MUTED);
        label.setText(message == null || message.isEmpty() ? " " : message);
    }

    private static String problem(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) root = root.getCause();
        String message = root.getMessage();
        return message == null || message.isBlank() ? "Operasi gagal. Coba lagi." : message;
    }

    private static void listen(JTextField field, Runnable change) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { change.run(); }
            public void removeUpdate(DocumentEvent e) { change.run(); }
            public void changedUpdate(DocumentEvent e) { change.run(); }
        });
    }

    private static JPanel column() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    private static void addField(JPanel form, String title, JComponent control) {
        JLabel caption = label(title);
        caption.setLabelFor(control);
        caption.setAlignmentX(Component.LEFT_ALIGNMENT);
        control.setAlignmentX(Component.LEFT_ALIGNMENT);
        control.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        form.add(caption);
        form.add(Box.createVerticalStrut(6));
        form.add(control);
        form.add(Box.createVerticalStrut(16));
    }

    private static JPanel surface() {
        JPanel panel = new JPanel(new BorderLayout(0, 16));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER), new EmptyBorder(22, 24, 22, 24)));
        return panel;
    }

    private static JLabel heading(String text) {
        JLabel result = label(text);
        result.setFont(result.getFont().deriveFont(Font.BOLD, 18f));
        return result;
    }

    private static JButton primary(String text) {
        JButton result = new JButton(text);
        result.setBackground(GREEN);
        result.setForeground(Color.WHITE);
        result.setFocusPainted(true);
        result.setMinimumSize(new Dimension(120, 36));
        result.setPreferredSize(new Dimension(Math.max(120, result.getPreferredSize().width + 14), 36));
        return result;
    }

    private static String points(int value) {
        return NumberFormat.getIntegerInstance(ID).format(value);
    }

    private static String rupiah(BigDecimal value) {
        return "Rp " + NumberFormat.getIntegerInstance(ID).format(value);
    }

    private static final class Login extends JDialog {
        private final JTextField username = new JTextField(24);
        private final JPasswordField password = new JPasswordField(24);
        private final JButton submit = primary("Masuk");
        private final JLabel message = status();
        private BankService service;
        private BankService.Admin admin;

        Login() {
            super((Frame) null, "Masuk | Bank Sampah", true);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            JPanel root = new JPanel(new BorderLayout(0, 20));
            root.setBackground(BACKGROUND);
            root.setBorder(new EmptyBorder(30, 36, 30, 36));
            JPanel title = column();
            title.add(heading("Bank Sampah"));
            title.add(Box.createVerticalStrut(6));
            title.add(label("Masuk sebagai petugas"));
            root.add(title, BorderLayout.NORTH);
            JPanel form = column();
            addField(form, "Username", username);
            addField(form, "Password", password);
            form.add(message);
            root.add(form, BorderLayout.CENTER);
            root.add(submit, BorderLayout.SOUTH);
            setContentPane(root);
            getRootPane().setDefaultButton(submit);
            getRootPane().registerKeyboardAction(e -> dispose(),
                    KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
            submit.addActionListener(e -> login());
            pack();
            setMinimumSize(new Dimension(390, getHeight()));
            setLocationRelativeTo(null);
            SwingUtilities.invokeLater(username::requestFocusInWindow);
        }

        private void login() {
            String name = username.getText().trim();
            char[] secret = password.getPassword();
            if (name.isEmpty() || secret.length == 0) {
                status(message, "Isi username dan password.", true);
                if (name.isEmpty()) username.requestFocusInWindow();
                else password.requestFocusInWindow();
                java.util.Arrays.fill(secret, '\0');
                return;
            }
            submit.setEnabled(false);
            username.setEnabled(false);
            password.setEnabled(false);
            status(message, "Memeriksa akun...", false);
            new SwingWorker<BankService.Admin, Void>() {
                private BankService candidate;
                protected BankService.Admin doInBackground() {
                    try {
                        candidate = new BankService(System.getProperty("banksampah.db.path", "banksampah.db"));
                        return candidate.login(name, secret);
                    } finally {
                        java.util.Arrays.fill(secret, '\0');
                    }
                }
                protected void done() {
                    try {
                        BankService.Admin result = get();
                        if (!isDisplayable()) return;
                        admin = result;
                        service = candidate;
                        password.setText("");
                        dispose();
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        status(message, "Masuk terhenti. Coba lagi.", true);
                    } catch (ExecutionException ex) {
                        status(message, problem(ex), true);
                        password.setText("");
                    } finally {
                        password.setText("");
                        submit.setEnabled(true);
                        username.setEnabled(true);
                        password.setEnabled(true);
                        if (admin == null) password.requestFocusInWindow();
                    }
                }
            }.execute();
        }
    }

    private static final class OperatorFrame extends JFrame {
        private final BankService service;
        private final BankService.Admin admin;
        private final JTabbedPane tabs = new JTabbedPane();
        private final JButton logout = new JButton("Keluar");
        private boolean mutating;
        private boolean optionsLoading;
        private boolean kategoriLoading;
        private int wargaRequest;
        private int optionsRequest;
        private int historyRequest;
        private int kategoriRequest;
        private int previewRequest;
        private List<BankService.Warga> wargaOptions = new ArrayList<>();

        private final JTextField nik = new JTextField();
        private final JTextField nama = new JTextField();
        private final JTextField hp = new JTextField();
        private final JTextField searchWarga = new JTextField();
        private final JButton saveWarga = primary("Tambah warga");
        private final JButton cancelEdit = new JButton("Batal edit");
        private final JButton editWarga = new JButton("Edit");
        private final JButton deleteWarga = new JButton("Hapus");
        private final JLabel wargaStatus = status();
        private final JLabel wargaListStatus = status();
        private final DefaultTableModel wargaModel = model("NIK", "Nama", "No. HP", "Saldo poin", "Poin receh");
        private final JTable wargaTable = table(wargaModel);
        private List<BankService.Warga> visibleWarga = new ArrayList<>();
        private BankService.Warga editing;
        private final Timer wargaTimer = debounce(320, this::loadWarga);

        private final JComboBox<BankService.Warga> setorWarga = new JComboBox<>();
        private final JComboBox<BankService.Kategori> kategori = new JComboBox<>();
        private final JTextField berat = new JTextField();
        private final JLabel setorStatus = status();
        private final JLabel optionsStatus = status();
        private final JLabel kategoriStatus = status();
        private final JLabel tarifListStatus = status();
        private final JLabel tarifStatus = status();
        private final JButton ubahTarif = new JButton("Ubah tarif");
        private final DefaultTableModel tarifModel = model("Kategori", "Nama", "Poin/kg");
        private final JTable tarifTable = table(tarifModel);
        private final JLabel previewStatus = status();
        private final JLabel preview = label("Pilih warga, kategori, dan isi berat.");
        private final JButton setorButton = primary("Catat setoran");
        private final Timer previewTimer = debounce(300, this::loadPreview);
        private BankService.Preview validPreview;

        private final JComboBox<BankService.Warga> tarikWarga = new JComboBox<>();
        private final JTextField poin = new JTextField();
        private final JComboBox<String> metode = new JComboBox<>(new String[] {"Tunai", "Saldo e-wallet"});
        private final JLabel tarikPreview = label("Masukkan poin yang akan ditarik.");
        private final JLabel tarikOptionsStatus = status();
        private final JLabel tarikStatus = status();
        private final JButton tarikButton = primary("Catat penarikan");
        private final JTextField searchNik = new JTextField();
        private final JComboBox<String> tipe = new JComboBox<>(new String[] {"Semua", "Setor", "Tarik"});
        private final JLabel historyStatus = status();
        private final JLabel exportStatus = status();
        private final JButton exportButton = new JButton("Ekspor PDF");
        private final DefaultTableModel historyModel = model("Tanggal", "Jenis", "NIK", "Nama", "Kategori", "Detail", "Poin", "Rupiah", "Status");
        private final JTable historyTable = table(historyModel);
        private final Timer historyTimer = debounce(320, this::loadHistory);

        OperatorFrame(BankService service, BankService.Admin admin) {
            super("Bank Sampah | Meja Operator");
            this.service = service;
            this.admin = admin;
            setDefaultCloseOperation(EXIT_ON_CLOSE);
            JPanel root = new JPanel(new BorderLayout(0, 18));
            root.setBackground(BACKGROUND);
            root.setBorder(new EmptyBorder(20, 24, 24, 24));
            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            JPanel title = column();
            title.add(heading("Bank Sampah"));
            title.add(label("Meja operator"));
            header.add(title, BorderLayout.WEST);
            JPanel account = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
            account.setOpaque(false);
            account.add(label("Petugas: " + admin.nama()));
            account.add(logout);
            header.add(account, BorderLayout.EAST);
            root.add(header, BorderLayout.NORTH);
            tabs.addTab("Warga", wargaPanel());
            tabs.addTab("Setor sampah", setorPanel());
            tabs.addTab("Tarik & riwayat", tarikPanel());
            root.add(tabs, BorderLayout.CENTER);
            setContentPane(root);
            setMinimumSize(new Dimension(1000, 680));
            setSize(1200, 760);
            setLocationRelativeTo(null);

            logout.addActionListener(e -> {
                if (JOptionPane.showConfirmDialog(this, "Keluar dari sesi petugas?", "Konfirmasi keluar",
                        JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
                    AdminSession.getInstance().logout();
                    dispose();
                    showLogin();
                }
            });
            tabs.addChangeListener(e -> {
                if (tabs.getSelectedIndex() == 0) loadWarga();
                if (tabs.getSelectedIndex() == 1) loadKategori();
                if (tabs.getSelectedIndex() == 2) loadHistory();
            });
            loadWarga();
            loadWargaOptions();
            loadKategori();
            updateActions();
        }

        private static DefaultTableModel model(String... columns) {
            return new DefaultTableModel(columns, 0) {
                public boolean isCellEditable(int row, int column) { return false; }
            };
        }

        private static JTable table(DefaultTableModel model) {
            JTable table = new JTable(model);
            table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            table.setFillsViewportHeight(true);
            table.setRowHeight(34);
            table.setAutoCreateRowSorter(true);
            table.getTableHeader().setReorderingAllowed(false);
            table.setShowVerticalLines(false);
            return table;
        }

        private static Timer debounce(int delay, Runnable task) {
            Timer timer = new Timer(delay, e -> task.run());
            timer.setRepeats(false);
            return timer;
        }

        private JPanel wargaPanel() {
            JPanel container = new JPanel(new BorderLayout(18, 0));
            container.setOpaque(false);
            container.setBorder(new EmptyBorder(12, 0, 0, 0));
            JPanel editor = surface();
            editor.setPreferredSize(new Dimension(298, 0));
            editor.add(heading("Data warga"), BorderLayout.NORTH);
            JPanel fields = column();
            addField(fields, "NIK", nik);
            addField(fields, "Nama lengkap", nama);
            addField(fields, "Nomor HP", hp);
            fields.add(label("Nomor HP: 8–15 angka, boleh diawali +."));
            editor.add(fields, BorderLayout.CENTER);
            JPanel actions = column();
            saveWarga.setAlignmentX(Component.LEFT_ALIGNMENT);
            cancelEdit.setAlignmentX(Component.LEFT_ALIGNMENT);
            wargaStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
            actions.add(saveWarga);
            actions.add(Box.createVerticalStrut(8));
            actions.add(cancelEdit);
            actions.add(Box.createVerticalStrut(12));
            actions.add(wargaStatus);
            editor.add(actions, BorderLayout.SOUTH);
            container.add(editor, BorderLayout.WEST);

            JPanel listing = surface();
            JPanel tools = new JPanel(new BorderLayout(10, 10));
            tools.setOpaque(false);
            tools.add(heading("Daftar warga"), BorderLayout.NORTH);
            JPanel filter = new JPanel(new BorderLayout(10, 0));
            filter.setOpaque(false);
            searchWarga.putClientProperty("JTextField.placeholderText", "Cari NIK atau nama");
            filter.add(label("Cari warga"), BorderLayout.WEST);
            filter.add(searchWarga, BorderLayout.CENTER);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            buttons.setOpaque(false);
            buttons.add(editWarga);
            buttons.add(deleteWarga);
            filter.add(buttons, BorderLayout.EAST);
            tools.add(filter, BorderLayout.CENTER);
            listing.add(tools, BorderLayout.NORTH);
            listing.add(new JScrollPane(wargaTable), BorderLayout.CENTER);
            listing.add(wargaListStatus, BorderLayout.SOUTH);
            container.add(listing, BorderLayout.CENTER);

            listen(searchWarga, () -> {
                ++wargaRequest;
                wargaTable.clearSelection();
                status(wargaListStatus, "Menunggu pencarian...", false);
                wargaTimer.restart();
            });
            wargaTable.getSelectionModel().addListSelectionListener(e -> updateActions());
            saveWarga.addActionListener(e -> saveWarga());
            cancelEdit.addActionListener(e -> clearEditor());
            editWarga.addActionListener(e -> {
                BankService.Warga selected = selectedWarga();
                if (selected == null) return;
                editing = selected;
                nik.setText(selected.nik());
                nama.setText(selected.nama());
                hp.setText(selected.hp());
                saveWarga.setText("Simpan perubahan");
                updateActions();
                nik.requestFocusInWindow();
            });
            deleteWarga.addActionListener(e -> deleteWarga());
            return container;
        }

        private JPanel setorPanel() {
            JPanel outer = new JPanel(new BorderLayout());
            outer.setOpaque(false);
            outer.setBorder(new EmptyBorder(12, 0, 0, 0));
            JPanel card = surface();
            card.setPreferredSize(new Dimension(570, 0));
            card.add(heading("Timbang dan catat setoran"), BorderLayout.NORTH);
            JPanel fields = column();
            setorWarga.setRenderer(wargaRenderer());
            kategori.setRenderer(kategoriRenderer());
            addField(fields, "Warga (NIK dan nama)", setorWarga);
            fields.add(optionsStatus);
            fields.add(Box.createVerticalStrut(8));
            addField(fields, "Kategori sampah", kategori);
            fields.add(kategoriStatus);
            fields.add(Box.createVerticalStrut(8));
            berat.putClientProperty("JTextField.placeholderText", "Contoh: 1,250 atau 1.250");
            addField(fields, "Berat (kg)", berat);
            JPanel estimate = column();
            estimate.add(label("Pratinjau poin"));
            estimate.add(Box.createVerticalStrut(8));
            preview.setFont(preview.getFont().deriveFont(Font.BOLD, 16f));
            estimate.add(preview);
            fields.add(estimate);
            fields.add(Box.createVerticalStrut(8));
            fields.add(previewStatus);
            card.add(fields, BorderLayout.CENTER);
            JPanel bottom = column();
            setorButton.setAlignmentX(Component.LEFT_ALIGNMENT);
            setorStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
            bottom.add(setorButton);
            bottom.add(Box.createVerticalStrut(12));
            bottom.add(setorStatus);
            card.add(bottom, BorderLayout.SOUTH);
            outer.add(card, BorderLayout.WEST);
            JPanel rates = surface();
            rates.add(heading("Tarif kategori"), BorderLayout.NORTH);
            rates.add(new JScrollPane(tarifTable), BorderLayout.CENTER);
            JPanel rateFooter = column();
            rateFooter.add(label("Tarif baru hanya berlaku untuk setoran berikutnya."));
            rateFooter.add(Box.createVerticalStrut(12));
            ubahTarif.setAlignmentX(Component.LEFT_ALIGNMENT);
            tarifListStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
            tarifStatus.setAlignmentX(Component.LEFT_ALIGNMENT);
            rateFooter.add(ubahTarif);
            rateFooter.add(Box.createVerticalStrut(8));
            rateFooter.add(tarifListStatus);
            rateFooter.add(tarifStatus);
            rates.add(rateFooter, BorderLayout.SOUTH);
            outer.add(rates, BorderLayout.CENTER);
            tarifTable.getSelectionModel().addListSelectionListener(e -> updateActions());
            ubahTarif.addActionListener(e -> editTarif());
            setorWarga.addActionListener(e -> invalidatePreview());
            kategori.addActionListener(e -> invalidatePreview());
            listen(berat, this::invalidatePreview);
            setorButton.addActionListener(e -> saveSetor());
            return outer;
        }

        private JPanel tarikPanel() {
            JPanel outer = new JPanel(new BorderLayout(0, 16));
            outer.setOpaque(false);
            outer.setBorder(new EmptyBorder(12, 0, 0, 0));
            JPanel withdraw = surface();
            withdraw.add(heading("Penarikan poin"), BorderLayout.WEST);
            JPanel form = new JPanel(new GridLayout(2, 2, 12, 8));
            form.setOpaque(false);
            tarikWarga.setRenderer(wargaRenderer());
            JLabel wargaCaption = label("Warga (NIK dan nama)");
            wargaCaption.setLabelFor(tarikWarga);
            JLabel poinCaption = label("Poin yang ditarik");
            poinCaption.setLabelFor(poin);
            form.add(wargaCaption);
            form.add(poinCaption);
            form.add(tarikWarga);
            form.add(poin);
            JPanel values = new JPanel(new BorderLayout(12, 0));
            values.setOpaque(false);
            values.add(form, BorderLayout.CENTER);
            JPanel side = column();
            addField(side, "Metode", metode);
            side.add(tarikOptionsStatus);
            side.add(tarikPreview);
            side.add(Box.createVerticalStrut(8));
            side.add(label("Minimum 1.000 poin, kelipatan 100 poin."));
            values.add(side, BorderLayout.SOUTH);
            withdraw.add(values, BorderLayout.CENTER);
            JPanel withdrawBottom = new JPanel(new BorderLayout(12, 0));
            withdrawBottom.setOpaque(false);
            withdrawBottom.add(tarikButton, BorderLayout.WEST);
            withdrawBottom.add(tarikStatus, BorderLayout.CENTER);
            withdraw.add(withdrawBottom, BorderLayout.SOUTH);
            outer.add(withdraw, BorderLayout.NORTH);

            JPanel history = surface();
            JPanel tools = new JPanel(new BorderLayout(10, 10));
            tools.setOpaque(false);
            tools.add(heading("Riwayat transaksi"), BorderLayout.NORTH);
            JPanel filter = new JPanel(new BorderLayout(10, 0));
            filter.setOpaque(false);
            searchNik.putClientProperty("JTextField.placeholderText", "Cari berdasarkan NIK");
            filter.add(label("NIK warga"), BorderLayout.WEST);
            filter.add(searchNik, BorderLayout.CENTER);
            tipe.setPreferredSize(new Dimension(110, 36));
            JPanel typeFilter = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            typeFilter.setOpaque(false);
            JLabel typeCaption = label("Jenis");
            typeCaption.setLabelFor(tipe);
            typeFilter.add(typeCaption);
            typeFilter.add(tipe);
            filter.add(typeFilter, BorderLayout.EAST);
            tools.add(filter, BorderLayout.CENTER);
            history.add(tools, BorderLayout.NORTH);
            history.add(new JScrollPane(historyTable), BorderLayout.CENTER);
            JPanel report = new JPanel(new BorderLayout(10, 0));
            report.setOpaque(false);
            report.add(exportButton, BorderLayout.WEST);
            JPanel messages = column();
            messages.add(historyStatus);
            messages.add(exportStatus);
            report.add(messages, BorderLayout.CENTER);
            history.add(report, BorderLayout.SOUTH);
            outer.add(history, BorderLayout.CENTER);

            listen(poin, this::updateTarikPreview);
            tarikWarga.addActionListener(e -> updateTarikPreview());
            tarikButton.addActionListener(e -> saveTarik());
            listen(searchNik, () -> {
                ++historyRequest;
                status(historyStatus, "Menunggu pencarian...", false);
                historyTimer.restart();
            });
            tipe.addActionListener(e -> loadHistory());
            exportButton.addActionListener(e -> exportPdf());
            return outer;
        }

        private static ListCellRenderer<? super BankService.Warga> wargaRenderer() {
            return (list, value, index, selected, focus) -> {
                JLabel cell = new JLabel(value == null ? "Pilih warga" : value.nik() + "  ·  " + value.nama());
                cell.setOpaque(true);
                cell.setBorder(new EmptyBorder(4, 8, 4, 8));
                cell.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
                cell.setForeground(selected ? list.getSelectionForeground() : INK);
                return cell;
            };
        }

        private static ListCellRenderer<? super BankService.Kategori> kategoriRenderer() {
            return (list, value, index, selected, focus) -> {
                JLabel cell = new JLabel(value == null ? "Pilih kategori" : value.nama() + "  ·  " + points(value.tarif()) + " poin/kg");
                cell.setOpaque(true);
                cell.setBorder(new EmptyBorder(4, 8, 4, 8));
                cell.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
                cell.setForeground(selected ? list.getSelectionForeground() : INK);
                return cell;
            };
        }

        private <T> void work(JButton button, JLabel feedback, String loading, Callable<T> job,
                              Consumer<T> success, Runnable finish) {
            work(button, feedback, loading, job, success, finish, () -> true, () -> { });
        }

        private <T> void work(JButton button, JLabel feedback, String loading, Callable<T> job,
                              Consumer<T> success, Runnable finish,
                              java.util.function.BooleanSupplier current, Runnable onError) {
            if (button != null) button.setEnabled(false);
            status(feedback, loading, false);
            new SwingWorker<T, Void>() {
                protected T doInBackground() throws Exception { return job.call(); }
                protected void done() {
                    try {
                        T result = get();
                        if (isDisplayable() && current.getAsBoolean()) success.accept(result);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        if (isDisplayable() && current.getAsBoolean()) {
                            status(feedback, "Proses terhenti. Coba lagi.", true);
                            onError.run();
                        }
                    } catch (ExecutionException ex) {
                        if (isDisplayable() && current.getAsBoolean()) {
                            status(feedback, problem(ex), true);
                            onError.run();
                        }
                    } finally {
                        if (button != null) button.setEnabled(true);
                        if (finish != null && isDisplayable()) finish.run();
                    }
                }
            }.execute();
        }

        private BankService.Warga selectedWarga() {
            int row = wargaTable.getSelectedRow();
            int index = row < 0 ? -1 : wargaTable.convertRowIndexToModel(row);
            return index >= 0 && index < visibleWarga.size() ? visibleWarga.get(index) : null;
        }

        private void updateActions() {
            editWarga.setEnabled(!mutating && selectedWarga() != null);
            deleteWarga.setEnabled(!mutating && selectedWarga() != null);
            saveWarga.setEnabled(!mutating);
            cancelEdit.setEnabled(!mutating && editing != null);
            setorButton.setEnabled(!mutating && !optionsLoading && !kategoriLoading && validPreview != null);
            ubahTarif.setEnabled(!mutating && !kategoriLoading && tarifTable.getSelectedRow() >= 0);
            tarikButton.setEnabled(!mutating && !optionsLoading && validPoin() != null);
            exportButton.setEnabled(!mutating);
            logout.setEnabled(!mutating);
        }

        private void mutation(boolean busy) {
            mutating = busy;
            nik.setEnabled(!busy);
            nama.setEnabled(!busy);
            hp.setEnabled(!busy);
            setorWarga.setEnabled(!busy);
            kategori.setEnabled(!busy);
            berat.setEnabled(!busy);
            tarikWarga.setEnabled(!busy);
            poin.setEnabled(!busy);
            metode.setEnabled(!busy);
            updateActions();
        }

        private void clearEditor() {
            editing = null;
            nik.setText("");
            nama.setText("");
            hp.setText("");
            saveWarga.setText("Tambah warga");
            updateActions();
            nik.requestFocusInWindow();
        }

        private void loadWarga() {
            int request = ++wargaRequest;
            String query = searchWarga.getText().trim();
            work(null, wargaListStatus, "Memuat warga...", () -> service.listWarga(query), rows -> {
                if (request != wargaRequest) return;
                visibleWarga = new ArrayList<>(rows);
                wargaModel.setRowCount(0);
                for (BankService.Warga row : rows) wargaModel.addRow(new Object[] {
                        row.nik(), row.nama(), row.hp(), points(row.saldoPoin()), row.poinReceh().toPlainString()});
                status(wargaListStatus, rows.isEmpty() ? "Tidak ada warga untuk pencarian ini." : " ", false);
                updateActions();
            }, null, () -> request == wargaRequest, () -> {
                visibleWarga = new ArrayList<>();
                wargaModel.setRowCount(0);
                updateActions();
            });
        }

        private void loadWargaOptions() {
            int request = ++optionsRequest;
            optionsLoading = true;
            updateActions();
            status(tarikOptionsStatus, "Memuat daftar warga...", false);
            work(null, optionsStatus, "Memuat daftar warga...", () -> service.listWarga(""), rows -> {
                if (request != optionsRequest) return;
                String selectedSetor = id((BankService.Warga) setorWarga.getSelectedItem());
                String selectedTarik = id((BankService.Warga) tarikWarga.getSelectedItem());
                wargaOptions = new ArrayList<>(rows);
                setorWarga.removeAllItems();
                tarikWarga.removeAllItems();
                for (BankService.Warga row : wargaOptions) {
                    setorWarga.addItem(row);
                    tarikWarga.addItem(row);
                }
                restoreWarga(setorWarga, selectedSetor);
                restoreWarga(tarikWarga, selectedTarik);
                String result = rows.isEmpty() ? "Belum ada warga. Tambahkan di tab Warga." : " ";
                status(optionsStatus, result, false);
                status(tarikOptionsStatus, result, false);
                optionsLoading = false;
                updateTarikPreview();
                invalidatePreview();
            }, null, () -> request == optionsRequest, () -> {
                setorWarga.removeAllItems();
                tarikWarga.removeAllItems();
                wargaOptions.clear();
                invalidatePreview();
                updateTarikPreview();
                status(tarikOptionsStatus, optionsStatus.getText(), true);
                optionsLoading = false;
                updateActions();
            });
        }

        private static String id(BankService.Warga warga) {
            return warga == null ? null : String.valueOf(warga.id());
        }

        private static void restoreWarga(JComboBox<BankService.Warga> combo, String id) {
            if (id == null) { combo.setSelectedIndex(-1); return; }
            for (int i = 0; i < combo.getItemCount(); i++) {
                if (String.valueOf(combo.getItemAt(i).id()).equals(id)) { combo.setSelectedIndex(i); return; }
            }
            combo.setSelectedIndex(-1);
        }

        private void loadKategori() {
            int request = ++kategoriRequest;
            kategoriLoading = true;
            invalidatePreview();
            status(tarifListStatus, "Memuat tarif...", false);
            String selected = kategori.getSelectedItem() instanceof BankService.Kategori k ? k.id() : null;
            int selectedRow = tarifTable.getSelectedRow();
            String selectedTarif = selectedRow < 0 ? null : (String) tarifModel.getValueAt(tarifTable.convertRowIndexToModel(selectedRow), 0);
            work(null, kategoriStatus, "Memuat kategori...", service::listKategori, rows -> {
                kategori.removeAllItems();
                tarifModel.setRowCount(0);
                for (BankService.Kategori item : rows) {
                    kategori.addItem(item);
                    tarifModel.addRow(new Object[] {item.id(), item.nama(), points(item.tarif())});
                }
                kategori.setSelectedIndex(-1);
                if (selected != null) for (int i = 0; i < kategori.getItemCount(); i++)
                    if (selected.equals(kategori.getItemAt(i).id())) kategori.setSelectedIndex(i);
                if (selectedTarif != null) for (int i = 0; i < tarifModel.getRowCount(); i++)
                    if (selectedTarif.equals(tarifModel.getValueAt(i, 0))) {
                        int row = tarifTable.convertRowIndexToView(i);
                        tarifTable.setRowSelectionInterval(row, row);
                    }
                kategoriLoading = false;
                status(kategoriStatus, rows.isEmpty() ? "Kategori belum tersedia." : " ", false);
                status(tarifListStatus, rows.isEmpty() ? "Kategori belum tersedia." : " ", false);
                invalidatePreview();
            }, null, () -> request == kategoriRequest, () -> {
                kategori.removeAllItems();
                tarifModel.setRowCount(0);
                status(tarifListStatus, kategoriStatus.getText(), true);
                kategoriLoading = false;
                invalidatePreview();
            });
        }

        private void editTarif() {
            int selected = tarifTable.getSelectedRow();
            if (selected < 0 || kategoriLoading || mutating) return;
            int row = tarifTable.convertRowIndexToModel(selected);
            String id = (String) tarifModel.getValueAt(row, 0);
            String namaKategori = (String) tarifModel.getValueAt(row, 1);
            JTextField input = new JTextField(12);
            JLabel caption = label("Tarif baru (poin/kg)");
            caption.setLabelFor(input);
            JPanel form = column();
            form.add(label(namaKategori + " (" + id + ")"));
            form.add(Box.createVerticalStrut(12));
            form.add(caption);
            form.add(Box.createVerticalStrut(6));
            form.add(input);
            if (JOptionPane.showConfirmDialog(this, form, "Ubah tarif kategori",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
            int value;
            try {
                value = Integer.parseInt(input.getText().trim());
                if (value <= 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                status(tarifStatus, "Tarif harus bilangan bulat lebih dari nol.", true);
                return;
            }
            mutation(true);
            work(ubahTarif, tarifStatus, "Menyimpan tarif...", () -> {
                service.updateTarifKategori(admin.id(), id, value);
                return null;
            }, ignored -> {
                status(tarifStatus, "Tarif " + namaKategori + " diperbarui. Riwayat lama tidak berubah.", false);
                loadKategori();
            }, () -> mutation(false));
        }

        private void saveWarga() {
            String n = nik.getText().trim(), name = nama.getText().trim(), phone = hp.getText().trim();
            if (n.isEmpty() || n.length() > 20 || name.isEmpty() || name.length() > 100) {
                status(wargaStatus, "Isi NIK (maks. 20 karakter) dan nama (maks. 100 karakter).", true);
                return;
            }
            if (phone.length() > 15 || !phone.matches("\\+?[0-9]{8,15}")) {
                status(wargaStatus, "Nomor HP: 8–15 angka, maks. 15 karakter termasuk +.", true);
                hp.requestFocusInWindow();
                return;
            }
            BankService.Warga current = editing;
            mutation(true);
            work(saveWarga, wargaStatus, "Menyimpan warga...", () -> {
                if (current == null) service.addWarga(n, name, phone);
                else service.updateWarga(current.id(), n, name, phone);
                return null;
            }, ignored -> {
                clearEditor();
                loadWarga();
                loadWargaOptions();
            }, () -> mutation(false));
        }

        private void deleteWarga() {
            BankService.Warga selected = selectedWarga();
            if (selected == null) return;
            if (JOptionPane.showConfirmDialog(this, "Hapus warga " + selected.nama() + " (" + selected.nik() + ")?",
                    "Konfirmasi hapus", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
            mutation(true);
            work(deleteWarga, wargaStatus, "Menghapus warga...", () -> {
                try {
                    service.deleteWarga(selected.id());
                } catch (RuntimeException ex) {
                    Throwable cause = ex;
                    while (cause != null) {
                        String text = cause.getMessage();
                        if (text != null && text.toLowerCase(Locale.ROOT).contains("foreign key"))
                            throw new IllegalStateException("Warga tidak dapat dihapus karena memiliki riwayat transaksi");
                        cause = cause.getCause();
                    }
                    throw ex;
                }
                return null;
            }, ignored -> {
                if (editing != null && editing.id() == selected.id()) clearEditor();
                loadWarga();
                loadWargaOptions();
            }, () -> mutation(false));
        }

        private void invalidatePreview() {
            ++previewRequest;
            previewTimer.stop();
            validPreview = null;
            preview.setText("Pilih warga, kategori, dan isi berat.");
            status(previewStatus, " ", false);
            updateActions();
            if (!optionsLoading && !kategoriLoading && setorWarga.getSelectedItem() != null
                    && kategori.getSelectedItem() != null && !berat.getText().isBlank())
                previewTimer.restart();
        }

        private void loadPreview() {
            BankService.Warga warga = (BankService.Warga) setorWarga.getSelectedItem();
            BankService.Kategori kat = (BankService.Kategori) kategori.getSelectedItem();
            String weight = berat.getText().trim().replace(',', '.');
            if (warga == null || kat == null || weight.isEmpty()) return;
            try {
                if (new BigDecimal(weight).signum() <= 0) throw new NumberFormatException();
            } catch (NumberFormatException ex) {
                preview.setText("Berat harus angka lebih dari nol.");
                return;
            }
            int request = ++previewRequest;
            preview.setText("Menghitung pratinjau...");
            work(null, previewStatus, "Menghitung poin...", () -> service.previewSetor(warga.id(), kat.id(), weight), result -> {
                if (request != previewRequest) return;
                validPreview = result;
                preview.setText("+" + result.tambahanPoin() + " poin  ·  receh akhir " + result.recehAkhir().toPlainString());
                status(previewStatus, "Poin setoran: " + result.poinDidapat().toPlainString() + " poin.", false);
                updateActions();
            }, null, () -> request == previewRequest, () -> preview.setText("Pratinjau tidak tersedia."));
        }

        private void saveSetor() {
            BankService.Warga warga = (BankService.Warga) setorWarga.getSelectedItem();
            BankService.Kategori kat = (BankService.Kategori) kategori.getSelectedItem();
            if (validPreview == null || warga == null || kat == null) return;
            String weight = berat.getText().trim().replace(',', '.');
            mutation(true);
            work(setorButton, setorStatus, "Mencatat setoran...", () -> {
                service.setor(admin.id(), warga.id(), kat.id(), weight);
                return null;
            }, ignored -> {
                berat.setText("");
                preview.setText("Setoran tercatat. Isi berat untuk setoran berikutnya.");
                status(setorStatus, "Setoran berhasil dicatat.", false);
                loadWarga();
                loadWargaOptions();
                if (tabs.getSelectedIndex() == 2) loadHistory();
            }, () -> mutation(false));
        }

        private Integer validPoin() {
            BankService.Warga warga = (BankService.Warga) tarikWarga.getSelectedItem();
            if (warga == null) return null;
            try {
                int value = Integer.parseInt(poin.getText().trim());
                return value >= 1000 && value % 100 == 0 && value <= warga.saldoPoin() ? value : null;
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        private void updateTarikPreview() {
            BankService.Warga warga = (BankService.Warga) tarikWarga.getSelectedItem();
            Integer value = validPoin();
            if (warga == null) tarikPreview.setText("Pilih warga terlebih dahulu.");
            else if (value == null) tarikPreview.setText("Saldo: " + points(warga.saldoPoin()) + " poin. Periksa jumlah tarik.");
            else tarikPreview.setText(points(value) + " poin = " + rupiah(BigDecimal.valueOf(value).multiply(BigDecimal.TEN)));
            updateActions();
        }

        private void saveTarik() {
            BankService.Warga warga = (BankService.Warga) tarikWarga.getSelectedItem();
            Integer value = validPoin();
            if (warga == null || value == null) {
                status(tarikStatus, "Poin harus min. 1.000, kelipatan 100, dan tidak melebihi saldo.", true);
                return;
            }
            String method = metode.getSelectedIndex() == 0 ? "CASH" : "E-WALLET";
            String amount = rupiah(BigDecimal.valueOf(value).multiply(BigDecimal.TEN));
            if (JOptionPane.showConfirmDialog(this, "Tarik " + points(value) + " poin (" + amount + ") untuk " + warga.nama() + "?",
                    "Konfirmasi penarikan", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
            mutation(true);
            work(tarikButton, tarikStatus, "Mencatat penarikan...", () -> {
                service.tarik(admin.id(), warga.id(), value, method);
                return null;
            }, ignored -> {
                poin.setText("");
                status(tarikStatus, "Penarikan berhasil dicatat.", false);
                loadWarga();
                loadWargaOptions();
                loadHistory();
            }, () -> mutation(false));
        }

        private String selectedType() {
            return switch (tipe.getSelectedIndex()) {
                case 1 -> "SETOR";
                case 2 -> "TARIK";
                default -> "SEMUA";
            };
        }

        private void loadHistory() {
            int request = ++historyRequest;
            String filterNik = searchNik.getText().trim();
            String type = selectedType();
            work(null, historyStatus, "Memuat riwayat...", () -> service.history(filterNik, type), rows -> {
                if (request != historyRequest) return;
                historyModel.setRowCount(0);
                for (BankService.History item : rows) historyModel.addRow(new Object[] {
                        item.tanggal(), item.jenis(), item.nik(), item.nama(), item.kategori(),
                        item.detail(), item.poin(), item.rupiah(), item.status()});
                status(historyStatus, rows.isEmpty() ? "Belum ada transaksi untuk filter ini." : "Riwayat hanya-baca.", false);
            }, null, () -> request == historyRequest, () -> historyModel.setRowCount(0));
        }

        private void exportPdf() {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Simpan laporan PDF");
            chooser.setSelectedFile(new java.io.File("laporan-bank-sampah.pdf"));
            if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            Path path = chooser.getSelectedFile().toPath();
            if (!path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".pdf"))
                path = path.resolveSibling(path.getFileName() + ".pdf");
            if (java.nio.file.Files.exists(path) && JOptionPane.showConfirmDialog(this,
                    "Timpa file " + path.getFileName() + "?", "Konfirmasi ekspor",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) return;
            Path target = path;
            String filterNik = searchNik.getText().trim();
            String type = selectedType();
            mutation(true);
            work(exportButton, exportStatus, "Membuat PDF...", () -> {
                service.exportPdf(target, filterNik, type);
                return null;
            }, ignored -> {
                status(exportStatus, "PDF disimpan: " + target.getFileName(), false);
                exportStatus.setToolTipText(target.toString());
            }, () -> mutation(false));
        }
    }
}
