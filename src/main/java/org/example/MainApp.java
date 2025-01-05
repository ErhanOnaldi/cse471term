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

    private JMenu menuFiles;
    private JMenuItem miConnect;
    private JMenuItem miDisconnect;
    private JMenuItem miExit;

    private JMenu menuOptions;
    private JMenuItem miSetRoot;
    private JMenuItem miSetDest;
    private JMenuItem miExcludeSubfolders;
    private JMenuItem miExcludeMasks;

    private JMenu menuHelp;
    private JMenuItem miAbout;

    private JTextField txtSearch;
    private JButton btnSearch;

    // ---- "Search Results" Tablosu ----
    private JTable tblResults;
    private DefaultTableModel tblModel;

    // ---- "Active Downloads" Tablosu ----
    private JTable tblDownloads;
    private DefaultTableModel downloadModel;

    private JButton btnDownload;

    private File rootFolder;
    private File destinationFolder;

    private P2PNode p2pNode;

    private Set<File> excludedSubfolders;
    private List<String> excludedDownloadMasks;

    // Map: fileHash -> tablo satır index (aktif indirmeler)
    private Map<String,Integer> downloadRowMap;

    public MainApp() {
        super("P2P File Sharing App");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(900, 600);

        p2pNode = new P2PNode();
        // Önemli: MainApp bu P2PNode'a "referans" verirse,
        // P2PNode -> MainApp'te progress bildiriminde bulunabilir.
        // Örneğin p2pNode.setGuiRef(this); (Göstereceğiz)

        excludedSubfolders = new HashSet<>();
        excludedDownloadMasks = new ArrayList<>();
        downloadRowMap = new HashMap<>();

        initMenu();
        initLayout();

        // GUI referansını p2pNode'a verelim (indirme ilerlemesi callback için)
        p2pNode.setGuiRef(this);
    }

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

        // ---- OPTIONS menüsü ----
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

        menuBar.add(menuFiles);
        menuBar.add(menuOptions);
        menuBar.add(menuHelp);

        setJMenuBar(menuBar);
    }

    private void initLayout() {
        // Üst panel: Search
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        txtSearch = new JTextField(20);
        btnSearch = new JButton("Search");
        btnSearch.addActionListener(e -> onSearch());

        topPanel.add(new JLabel("Search:"));
        topPanel.add(txtSearch);
        topPanel.add(btnSearch);

        add(topPanel, BorderLayout.NORTH);

        // Orta panel -> arama sonuçları
        tblModel = new DefaultTableModel(new String[]{"File Name", "File Hash", "File Size"}, 0) {
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tblResults = new JTable(tblModel);
        JScrollPane scrollResults = new JScrollPane(tblResults);

        // "Aktif İndirmeler" tablosu
        downloadModel = new DefaultTableModel(new String[]{"File Hash", "Progress (%)"}, 0) {
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tblDownloads = new JTable(downloadModel);
        JScrollPane scrollDownloads = new JScrollPane(tblDownloads);

        // SplitPane -> solda arama sonuçları, sağda aktif indirmeler (örnek)
        JSplitPane splitPane = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                scrollResults,
                scrollDownloads
        );
        splitPane.setDividerLocation(500);

        add(splitPane, BorderLayout.CENTER);

        // Altta Download butonu
        btnDownload = new JButton("Download Selected");
        btnDownload.addActionListener(e -> onDownloadSelected());
        add(btnDownload, BorderLayout.SOUTH);
    }

    // -------------- Root & Destination Folder --------------
    private void chooseRootFolder() {
        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        int result = fc.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            rootFolder = fc.getSelectedFile();
            p2pNode.setRootFolder(rootFolder);
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

    // -------------- Exclude Subfolders & Masks --------------
    private void excludeSubfoldersDialog() {
        if (rootFolder == null) {
            JOptionPane.showMessageDialog(this,
                    "Root folder not set!",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        List<File> subFolders = findSubfolders(rootFolder);
        if (subFolders.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No subfolders found under root.",
                    "Info",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }

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
            p2pNode.setExcludedSubfolders(excludedSubfolders);
            JOptionPane.showMessageDialog(this,
                    "Excluded " + selected.size() + " subfolder(s).");
        }
    }

    private List<File> findSubfolders(File folder) {
        List<File> result = new ArrayList<>();
        File[] files = folder.listFiles();
        if (files == null) return result;
        for (File f : files) {
            if (f.isDirectory()) {
                result.add(f);
            }
        }
        return result;
    }

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

    // -------------- Connect / Disconnect / Exit --------------
    private void onConnect() {
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

    private void showAboutDialog() {
        JOptionPane.showMessageDialog(this,
                "P2P File Sharing Application\nDeveloper: Your Name\nVersion: 1.0\n\n" +
                        "Bonus:\n- Exclude subfolders\n- Exclude download masks\n",
                "About",
                JOptionPane.INFORMATION_MESSAGE);
    }

    // -------------- Search --------------
    private void onSearch() {
        String query = txtSearch.getText().trim();
        if (query.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a search keyword.");
            return;
        }
        clearSearchResults();
        p2pNode.searchFile(query);
        JOptionPane.showMessageDialog(this,
                "SEARCH packet sent for: " + query + "\nWaiting for responses...");
    }

    private void clearSearchResults() {
        while (tblModel.getRowCount() > 0) {
            tblModel.removeRow(0);
        }
    }

    public void addSearchResults(String responseData) {
        String[] lines = responseData.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split("\\|");
            if (parts.length >= 2) {
                String fName = parts[0];
                String fHash = parts[1];
                String fSize = (parts.length >= 3) ? parts[2] : "?";
                tblModel.addRow(new Object[]{fName, fHash, fSize});
            }
        }
    }

    // -------------- Download --------------
    private void onDownloadSelected() {
        int row = tblResults.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Please select a row from results table.");
            return;
        }
        String fileName = (String) tblModel.getValueAt(row, 0);
        String fileHash = (String) tblModel.getValueAt(row, 1);
        String fileSizeStr = (String) tblModel.getValueAt(row, 2);

        // Mask kontrolü
        if (matchesExcludeMask(fileName)) {
            JOptionPane.showMessageDialog(this,
                    "This file is excluded by download mask!\n(" + fileName + ")");
            return;
        }

        // Dosya boyutu bilinmiyorsa, dummy 2MB
        long size;
        try {
            size = Long.parseLong(fileSizeStr);
        } catch (NumberFormatException ex) {
            size = 2L * 1024L * 1024L;
        }

        // 1) Tablomuza "0%" indirme satırı ekleyelim
        //    Sütunlar: "File Hash", "Progress (%)"
        int newRow = downloadModel.getRowCount();
        downloadModel.addRow(new Object[]{ fileHash, "0.0" });
        downloadRowMap.put(fileHash, newRow);

        // 2) İndirmeyi başlat
        p2pNode.downloadFile(fileHash, size);
        JOptionPane.showMessageDialog(this,
                "Download started for: " + fileName + "\nHash=" + fileHash);
    }

    private boolean matchesExcludeMask(String fileName) {
        fileName = fileName.toLowerCase();
        for (String mask : excludedDownloadMasks) {
            String m = mask.toLowerCase().trim();
            if (m.startsWith("*.")) {
                String ext = m.substring(1);
                if (fileName.endsWith(ext)) {
                    return true;
                }
            } else if (m.endsWith("*")) {
                String prefix = m.substring(0, m.length() - 1);
                if (fileName.startsWith(prefix)) {
                    return true;
                }
            } else if (m.equals(fileName)) {
                return true;
            }
        }
        return false;
    }

    // -------------- (YENİ) Indirme Progress Güncellemesi --------------
    /**
     * P2PNode veya DownloadManager bu metodu çağırarak
     * GUI tarafında indirme yüzdesini günceller.
     */
    public void updateDownloadProgress(String fileHash, double percent) {
        // Bu metot, indirme ilerlemesini tabloya yansıtacak
        // Swing thread'inde çalışmayı garanti edelim:
        SwingUtilities.invokeLater(() -> {
            // tablodaki satırı bul
            Integer rowIndex = downloadRowMap.get(fileHash);
            if (rowIndex == null) {
                // Bu hash tabloya eklenmemiş, belki multi-source vs.
                // isterseniz otomatik ekleyebilirsiniz, ama şu an yok sayıyoruz.
                return;
            }
            // Sütun 1 -> "Progress (%)"
            downloadModel.setValueAt(String.format("%.2f", percent), rowIndex, 1);
        });
    }

    // ---------------------------------------------------------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainApp app = new MainApp();
            app.setVisible(true);
        });
    }
}
