package org.example;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.*;
import java.util.List;

public class MainApp extends JFrame {

    private JMenuBar menuBar;

    // "Files" menüsü
    private JMenu menuFiles;
    private JMenuItem miConnect;
    private JMenuItem miDisconnect;
    private JMenuItem miExit;

    // "Options" menüsü (bonus özellikler & klasör seçimleri)
    private JMenu menuOptions;
    private JMenuItem miSetRoot;
    private JMenuItem miSetDest;
    private JMenuItem miExcludeSubfolders;
    private JMenuItem miExcludeMasks;

    // "Help" menüsü
    private JMenu menuHelp;
    private JMenuItem miAbout;

    // Arama alanları
    private JTextField txtSearch;
    private JButton btnSearch;

    // Arama sonuçlarını göstermek için tablo
    private JTable tblResults;
    private DefaultTableModel tblModel;

    // "Download" butonu
    private JButton btnDownload;

    // Klasörler
    private File rootFolder;
    private File destinationFolder;

    // P2P iş mantığını yöneten sınıfımız
    private P2PNode p2pNode;

    // BONUS: Paylaşım dışı bırakılacak alt klasörler
    // (rootFolder altında, kullanıcı bu klasörleri hariç tutmak istedi)
    private Set<File> excludedSubfolders;

    // BONUS: İndirme esnasında hariç tutulacak dosya mask’ları (örn. *.txt, secret_*, vb.)
    private List<String> excludedDownloadMasks;

    public MainApp() {
        super("P2P File Sharing App");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(900, 600);

        // Temel veri yapıları
        p2pNode = new P2PNode();
        excludedSubfolders = new HashSet<>();
        excludedDownloadMasks = new ArrayList<>();

        initMenu();
        initLayout();
    }

    // ------------------------------------------------------------------
    //  Menü Oluşturma
    // ------------------------------------------------------------------
    private void initMenu() {
        menuBar = new JMenuBar();

        // ---- FILES menüsü ----
        menuFiles = new JMenu("Files");

        miConnect = new JMenuItem("Connect");
        miConnect.addActionListener(e -> onConnect());

        miDisconnect = new JMenuItem("Disconnect");
        miDisconnect.addActionListener(e -> onDisconnect());

        miExit = new JMenuItem("Exit");
        miExit.addActionListener(e -> onExit());

        menuFiles.add(miConnect);
        menuFiles.add(miDisconnect);
        menuFiles.addSeparator();
        menuFiles.add(miExit);

        // ---- OPTIONS menüsü (Bonus ayarlar) ----
        menuOptions = new JMenu("Options");

        miSetRoot = new JMenuItem("Set Root Folder");
        miSetRoot.addActionListener(e -> chooseRootFolder());

        miSetDest = new JMenuItem("Set Download Folder");
        miSetDest.addActionListener(e -> chooseDestinationFolder());

        miExcludeSubfolders = new JMenuItem("Exclude Subfolders...");
        miExcludeSubfolders.addActionListener(e -> excludeSubfoldersDialog());

        miExcludeMasks = new JMenuItem("Exclude Download Masks...");
        miExcludeMasks.addActionListener(e -> excludeMasksDialog());

        menuOptions.add(miSetRoot);
        menuOptions.add(miSetDest);
        menuOptions.addSeparator();
        menuOptions.add(miExcludeSubfolders);
        menuOptions.add(miExcludeMasks);

        // ---- HELP menüsü ----
        menuHelp = new JMenu("Help");

        miAbout = new JMenuItem("About");
        miAbout.addActionListener(e -> showAboutDialog());

        menuHelp.add(miAbout);

        // Menü çubuğuna ekleyelim
        menuBar.add(menuFiles);
        menuBar.add(menuOptions);
        menuBar.add(menuHelp);

        setJMenuBar(menuBar);
    }

    // ------------------------------------------------------------------
    //  Arayüz Bileşenleri
    // ------------------------------------------------------------------
    private void initLayout() {
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        txtSearch = new JTextField(20);
        btnSearch = new JButton("Search");

        btnSearch.addActionListener(e -> onSearch());

        topPanel.add(new JLabel("Search:"));
        topPanel.add(txtSearch);
        topPanel.add(btnSearch);

        add(topPanel, BorderLayout.NORTH);

        // Arama sonuçlarını göstermek için tablo
        tblModel = new DefaultTableModel(new String[]{"File Name", "File Hash", "File Size"}, 0) {
            // Hücrelerin düzenlenmesini engellemek adına
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tblResults = new JTable(tblModel);
        JScrollPane scrollPane = new JScrollPane(tblResults);
        add(scrollPane, BorderLayout.CENTER);

        // Download butonu
        btnDownload = new JButton("Download Selected");
        btnDownload.addActionListener(e -> onDownloadSelected());
        add(btnDownload, BorderLayout.SOUTH);
    }

    // ------------------------------------------------------------------
    //  Root & Destination Klasör Seçimi
    // ------------------------------------------------------------------
    private void chooseRootFolder() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int result = fc.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            rootFolder = fc.getSelectedFile();
            // Root klasör node'a bildir
            p2pNode.setRootFolder(rootFolder);
            // Bonus: Kendi excludeSubfolders logic'inize entegre edebilirsiniz
            JOptionPane.showMessageDialog(this,
                    "Root folder set: " + rootFolder.getAbsolutePath());
        }
    }

    private void chooseDestinationFolder() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int result = fc.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            destinationFolder = fc.getSelectedFile();
            p2pNode.setDestinationFolder(destinationFolder);
            JOptionPane.showMessageDialog(this,
                    "Destination folder set: " + destinationFolder.getAbsolutePath());
        }
    }

    // ------------------------------------------------------------------
    //  Exclude Subfolders Dialog (Bonus 10 points)
    // ------------------------------------------------------------------
    /**
     * Kullanıcıya rootFolder altındaki alt klasörleri listeler ve
     * hangilerini hariç tutmak istediğini seçmesine izin verir.
     */
    private void excludeSubfoldersDialog() {
        if (rootFolder == null) {
            JOptionPane.showMessageDialog(this,
                    "Root folder not set!",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        // Root klasör altındaki alt klasörleri bulalım (tek seviye / rekurzif)
        List<File> subFolders = findSubfolders(rootFolder);
        if (subFolders.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No subfolders found under root.",
                    "Info",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Bir JList gösterelim
        DefaultListModel<File> listModel = new DefaultListModel<>();
        for (File f : subFolders) {
            listModel.addElement(f);
        }
        JList<File> jList = new JList<>(listModel);
        jList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        int choice = JOptionPane.showConfirmDialog(
                this,
                new JScrollPane(jList),
                "Select Subfolders to Exclude from Sharing",
                JOptionPane.OK_CANCEL_OPTION
        );
        if (choice == JOptionPane.OK_OPTION) {
            List<File> selected = jList.getSelectedValuesList();
            for (File f : selected) {
                excludedSubfolders.add(f);
            }
            JOptionPane.showMessageDialog(this,
                    "Excluded " + selected.size() + " subfolder(s).");
        }
    }

    /**
     * root altında rekurzif olarak alt klasörleri bulur. (Sadece bir seviye istiyorsanız
     * isDirectory() kontrolü ile tek adımda durabilirsiniz.)
     */
    private List<File> findSubfolders(File folder) {
        List<File> result = new ArrayList<>();
        File[] files = folder.listFiles();
        if (files == null) return result;
        for (File f : files) {
            if (f.isDirectory()) {
                result.add(f);
                // İsterseniz rekurzif altına da inebilirsiniz
                // result.addAll(findSubfolders(f));
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    //  Exclude Download Masks Dialog (Bonus 10 points)
    // ------------------------------------------------------------------
    /**
     * Kullanıcı, "*.mp4" veya "secret_*" gibi maskeler girebilir.
     * Bu dosya isimleri/mask’ları indirmede hariç tutulur.
     */
    private void excludeMasksDialog() {
        String existing = String.join(";", excludedDownloadMasks);
        String input = JOptionPane.showInputDialog(
                this,
                "Enter file masks to exclude (separate by semicolon):",
                existing
        );
        if (input != null) {
            excludedDownloadMasks.clear();
            String[] parts = input.split(";");
            for (String p : parts) {
                String trimmed = p.trim();
                if (!trimmed.isEmpty()) {
                    excludedDownloadMasks.add(trimmed);
                }
            }
            JOptionPane.showMessageDialog(this,
                    "Updated exclude masks. Current: " + excludedDownloadMasks.toString());
        }
    }

    // ------------------------------------------------------------------
    //  Connect / Disconnect / Exit
    // ------------------------------------------------------------------
    private void onConnect() {
        // NOT: Subfolder exclusion logic'inizi P2PNode’un "shareLocalFiles()"
        // metoduna entegre etmelisiniz. Orada "excludedSubfolders" kontrol edilerek
        // taramada atlanabilir. Aynı şekilde "excludedDownloadMasks"
        // download öncesi kontrol edilebilir.
        p2pNode.connect();
        JOptionPane.showMessageDialog(this, "Connected to P2P overlay.");
    }

    private void onDisconnect() {
        p2pNode.disconnect();
        JOptionPane.showMessageDialog(this, "Disconnected from P2P overlay.");
    }

    private void onExit() {
        p2pNode.shutdown();
        dispose();
        System.exit(0);
    }

    // ------------------------------------------------------------------
    //  Help / About
    // ------------------------------------------------------------------
    private void showAboutDialog() {
        JOptionPane.showMessageDialog(this,
                "P2P File Sharing Application\nDeveloper: Your Name\nVersion: 1.0\n\n" +
                        "Bonus:\n- Exclude subfolders\n- Exclude download masks\n",
                "About",
                JOptionPane.INFORMATION_MESSAGE);
    }

    // ------------------------------------------------------------------
    //  Arama & Sonuç Gösterimi
    // ------------------------------------------------------------------
    /**
     * Kullanıcı "Search" butonuna tıklayınca
     * P2PNode üstünden "searchFile" çağrısı yapıyoruz.
     * Gelen "SEARCH_RESPONSE" paketleri, p2pNode.handleIncomingPacket ->
     * orada "case SEARCH_RESPONSE" -> ...
     * oradan GUI'yi güncelleyebiliriz (Event dispatch thread'te).
     */
    private void onSearch() {
        String query = txtSearch.getText().trim();
        if (query.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a search keyword.");
            return;
        }
        // Tablodaki sonuçları temizleyelim
        clearSearchResults();

        // Arama gönder
        p2pNode.searchFile(query);
        JOptionPane.showMessageDialog(this, "SEARCH packet sent for: " + query
                + "\nWaiting for responses...");

        // Bu örnekte, gelen yanıtları tabloya otomatik ekleyeceğiz.
        // Bunu yapabilmek için p2pNode.handleIncomingPacket(...),
        // SEARCH_RESPONSE geldiğinde,
        // MainApp'e callback gibi haber vermesi gerekir.
        // Örneğin: `MainApp.this.addSearchResults(...)`
        // Aşağıda "addSearchResults(...)" metodunu tanımlıyoruz.
    }

    // Tabloyu temizle
    private void clearSearchResults() {
        while (tblModel.getRowCount() > 0) {
            tblModel.removeRow(0);
        }
    }

    /**
     * Arama yanıtlarını tabloya eklemek için (p2pNode -> MainApp)
     * bir metod.
     * Projede p2pNode'a "GUI callback" vererek oradan çağırabilir ya da
     * Observer pattern kullanabilirsiniz.
     */
    public void addSearchResults(String responseData) {
        // responseData formatımız:
        // "filename|hash\nfilename2|hash2\n..." gibi
        // Örnek olarak parse edelim
        String[] lines = responseData.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split("\\|");
            if (parts.length >= 2) {
                String fName = parts[0];
                String fHash = parts[1];
                // Boyut bilinmiyorsa "?".
                // Gelişmiş senaryoda oraya boyutu da ekleyebilirsiniz.
                tblModel.addRow(new Object[]{fName, fHash, "?"});
            }
        }
    }

    // ------------------------------------------------------------------
    //  Download Seçimi
    // ------------------------------------------------------------------
    /**
     * Tablodan seçilen satırın fileHash'i alınır, download başlatılır.
     * "excludedDownloadMasks" kontrolü de burada veya p2pNode.downloadFile(...) içinde yapılabilir.
     */
    private void onDownloadSelected() {
        int row = tblResults.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a row from results table.");
            return;
        }
        String fileName = (String) tblModel.getValueAt(row, 0);
        String fileHash = (String) tblModel.getValueAt(row, 1);

        // BONUS: indirme hariç tutma mask kontrolü
        if (matchesExcludeMask(fileName)) {
            JOptionPane.showMessageDialog(this,
                    "This file is excluded by download mask!\n(" + fileName + ")");
            return;
        }

        // Boyutu tabloya "?" koymuşsak, net boyutu bilmiyoruz.
        // Örnek olarak 2MB diyelim. Gerçekte arama yanıtında boyutu da göndermelisiniz.
        long dummySize = 2L * 1024L * 1024L; // 2MB
        p2pNode.downloadFile(fileHash, dummySize);
        JOptionPane.showMessageDialog(this,
                "Download started for: " + fileName + "\nHash=" + fileHash);
    }

    /**
     * Dosya adını excludedDownloadMasks listesiyle karşılaştırır.
     * Basit wildcard veya "startsWith/endsWith" vs. yapabilirsiniz.
     * Aşağıdaki örnek, '*' işaretini kabaca ele alan bir yaklaşım.
     */
    private boolean matchesExcludeMask(String fileName) {
        fileName = fileName.toLowerCase();
        for (String mask : excludedDownloadMasks) {
            String m = mask.toLowerCase().trim();
            // Örneğin "*.mp3" -> endsWith(".mp3")
            // "secret_*" -> startsWith("secret_")
            if (m.startsWith("*.")) {
                // extension bazlı
                String ext = m.substring(1); // ".mp3"
                if (fileName.endsWith(ext)) {
                    return true;
                }
            } else if (m.endsWith("*")) {
                // prefix bazlı
                String prefix = m.substring(0, m.length() - 1);
                if (fileName.startsWith(prefix)) {
                    return true;
                }
            } else if (m.equals(fileName)) {
                // Tam eşleşme
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    //  main()
    // ------------------------------------------------------------------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainApp app = new MainApp();
            app.setVisible(true);

            // Eğer p2pNode'dan SEARCH_RESPONSE geldiğinde tabloyu güncellemek isterseniz,
            // p2pNode'a "app" referansı verebilir, oradan "addSearchResults(...)" çağırabilirsiniz.
            // Örn: p2pNode.setGuiCallback(app);
        });
    }
}
