package com.EmployeeDataProcessor;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.*;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// --- models: compact records for efficient data representation ---
record Employee(String name, int age, String department, double salary) {}
record LoadResult(List<Employee> employees, Object[][] tableData, String[] headers) {}
record ColumnState(String header, int modelIndex, JCheckBox checkBox, TableColumn column) {}
record FilterResult(RowFilter<Object, Object> filter, Map<Integer, Object[]> state) {}

class CSVLoader { // parses CSV data into structured objects
    public static LoadResult load(File file, Charset charset) throws Exception {
        if (file.length() > 10 * 1024 * 1024) throw new Exception("file size exceeds 10MB limit.");
        List<String[]> lines;
        try (Stream<String> lineStream = Files.lines(file.toPath(), charset)) { lines = lineStream.map(line -> line.split(",", -1)).toList(); }
        if (lines.isEmpty()) throw new Exception("CSV file is empty.");
        String[] headers = Arrays.stream(lines.get(0)).map(h -> h.trim().replace("\"", "")).toArray(String[]::new);
        int nameIdx = findHIdx(headers, "name"), ageIdx = findHIdx(headers, "age"), deptIdx = findHIdx(headers, "department", "dept"), salIdx = findHIdx(headers, "salary");
        if ((nameIdx == -1 && findHIdx(headers, "firstname") == -1) || ageIdx == -1 || deptIdx == -1 || salIdx == -1) throw new Exception("CSV requires: Age, Department, Salary, and Name/FirstName.");
        
        List<Employee> empList = new ArrayList<>();
        Object[][] data = new Object[lines.size() - 1][headers.length];
        for (int i = 1; i < lines.size(); i++) {
            String[] p = lines.get(i);
            try {
                String name = (nameIdx != -1) ? p[nameIdx].trim().replace("\"", "") : "N/A";
                int age = Integer.parseInt(p[ageIdx].trim());
                String dept = p[deptIdx].trim().replace("\"", "");
                double salary = Double.parseDouble(p[salIdx].replaceAll("[^\\d.]", "").trim());
                empList.add(new Employee(name, age, dept, salary));
                for (int j = 0; j < headers.length; j++) {
                    String v = (j < p.length) ? p[j].trim().replace("\"", "") : ""; String h = headers[j].toLowerCase();
                    if (h.contains("age") && v.matches("\\d+")) data[i - 1][j] = Integer.parseInt(v);
                    else if ((h.contains("salary") || h.contains("bonus")) && !v.isEmpty()) data[i-1][j] = Double.parseDouble(v.replaceAll("[^\\d.]",""));
                    else data[i-1][j] = v;
                }
            } catch (Exception e) { /* ignore malformed rows */ }
        }
        return new LoadResult(empList, data, headers);
    }
    public static int findHIdx(String[] h, String... n) { return IntStream.range(0, h.length).filter(i -> Arrays.stream(n).anyMatch(h[i].trim().toLowerCase()::contains)).findFirst().orElse(-1); }
}

class UIHelper { // ui helpers to reduce code duplication
    public static void setTextFieldLimit(JTextField f, int l) { ((AbstractDocument)f.getDocument()).setDocumentFilter(new DocumentFilter(){ @Override public void replace(FilterBypass fb, int o, int len, String s, AttributeSet a) throws BadLocationException { if((fb.getDocument().getLength()+(s!=null?s.length():0)-len)<=l) super.replace(fb,o,len,s,a); } }); }
    public static void adjustColumnWidths(JTable table) {
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int c = 0; c < table.getColumnCount(); c++) {
            TableColumn tc = table.getColumnModel().getColumn(c);
            int w = (int)table.getTableHeader().getDefaultRenderer().getTableCellRendererComponent(table,tc.getHeaderValue(),false,false,-1,c).getPreferredSize().getWidth();            for (int r = 0; r < Math.min(table.getRowCount(), 50); r++) w = Math.max(w, table.prepareRenderer(table.getCellRenderer(r, c), r, c).getPreferredSize().width);
            tc.setPreferredWidth(Math.min(w + 15, 400));
        }
    }
}

@FunctionalInterface interface SimpleDocListener extends DocumentListener { void update(DocumentEvent e); @Override default void insertUpdate(DocumentEvent e){update(e);} @Override default void removeUpdate(DocumentEvent e){update(e);} @Override default void changedUpdate(DocumentEvent e){} }

@SuppressWarnings("serial")
class ImportFrame extends JFrame {
    private final JTextField filePathField = new JTextField(40);
    private final JComboBox<String> encodingBox = new JComboBox<>(new String[]{"UTF-8", "ISO-8859-1"});
    public ImportFrame() {
        super("Employee Data Processor - Import"); setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); setSize(550, 220); setLocationRelativeTo(null);
        var mainPanel = new JPanel(new BorderLayout(10, 10)) {{ setBorder(BorderFactory.createEmptyBorder(10,10,10,10)); }};
        var topPanel = new JPanel(new GridLayout(2,1,5,5));
        topPanel.add(new JPanel(new FlowLayout(FlowLayout.LEFT)) {{ add(new JLabel("Dataset Path:")); add(filePathField); add(new JButton("..."){{addActionListener(_->browseFile());}}); }});
        topPanel.add(new JPanel(new FlowLayout(FlowLayout.LEFT)) {{ add(new JLabel("Encoding:")); add(encodingBox); }});
        var info = new JTextArea("Required CSV Columns: Name, Age, Department and Salary.") {{ setEditable(false); setFont(new Font("SansSerif", Font.ITALIC, 11)); setBackground(getBackground()); }};
        mainPanel.add(topPanel, BorderLayout.NORTH); mainPanel.add(info, BorderLayout.CENTER); mainPanel.add(new JButton("Import & Launch"){{addActionListener(_->importData());}}, BorderLayout.SOUTH);
        add(mainPanel);
    }
    private void browseFile() { var fd = new FileDialog(this, "Select CSV", FileDialog.LOAD); fd.setFilenameFilter((_, name) -> name.endsWith(".csv")); fd.setVisible(true); if(fd.getFile()!=null) filePathField.setText(fd.getDirectory()+fd.getFile()); }
    private void importData() {
        if(filePathField.getText().isEmpty()){ JOptionPane.showMessageDialog(this,"Select file.","Error",0); return; }
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try {
            var r = CSVLoader.load(new File(filePathField.getText()), Charset.forName((String)encodingBox.getSelectedItem()));
            if (r.employees().isEmpty()) throw new Exception("No valid employee data read from file.");
            dispose(); SwingUtilities.invokeLater(()->new MainFrame(r.employees(),r.headers(),r.tableData(),filePathField.getText()).setVisible(true));
        } catch (Exception e) { JOptionPane.showMessageDialog(this, "Failed to load file:\n"+e.getMessage(), "Import Error",0); }
        finally { setCursor(Cursor.getDefaultCursor()); }
    }
}

@SuppressWarnings("serial")
class MainFrame extends JFrame {
    private final JTable table; private final DefaultTableModel tblModel; private final TableRowSorter<DefaultTableModel> sorter;
    private final List<Employee> allEmployees; private final String[] allHeaders;
    private final JTextField searchField = new JTextField(20); private RowFilter<Object,Object> advFilter = null;
    private Map<Integer, Object[]> advFilterState = new HashMap<>(); private final JButton advBtn = new JButton("Filter...");
    private final JButton colBtn = new JButton("Columns..."); private final JLabel statusLbl = new JLabel(), avgSalLbl = new JLabel("Avg: ---");
    public MainFrame(List<Employee> emps, String[] hdrs, Object[][] data, String fName) {
        super("Employee Data Processor - "+new File(fName).getName());
        this.allEmployees=emps; this.allHeaders=hdrs; setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); setSize(1200, 700); setLocationRelativeTo(null); setLayout(new BorderLayout(10,10));
        tblModel = new DefaultTableModel(data, hdrs){ @Override public boolean isCellEditable(int r, int c){return false;} @Override public Class<?> getColumnClass(int c){return (getRowCount()>0&&getValueAt(0,c)!=null)?getValueAt(0,c).getClass():Object.class;} };
        table = new JTable(tblModel); sorter = new TableRowSorter<>(tblModel); table.setRowSorter(sorter);
        UIHelper.setTextFieldLimit(searchField, 50); searchField.getDocument().addDocumentListener((SimpleDocListener)_ -> applyFilters());
        table.addMouseListener(new MouseAdapter(){ public void mouseClicked(MouseEvent e){ if(e.getClickCount()==2&&table.getSelectedRow()!=-1){int r=table.convertRowIndexToModel(table.getSelectedRow()), nIdx=CSVLoader.findHIdx(allHeaders, "name","firstname","lastname"), dIdx=CSVLoader.findHIdx(allHeaders,"department"); String n=(nIdx!=-1)?tblModel.getValueAt(r,nIdx).toString():"?", d=(dIdx!=-1)?tblModel.getValueAt(r,dIdx).toString():"?"; JOptionPane.showMessageDialog(MainFrame.this,n+" from "+d,"Details",1);}}});
        add(createToolbar(), BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(table); sp.setBorder(BorderFactory.createEtchedBorder()); add(sp, BorderLayout.CENTER);
        JPanel sPnl = new JPanel(new FlowLayout(FlowLayout.LEFT)); sPnl.add(statusLbl); add(sPnl, BorderLayout.SOUTH);
        UIHelper.adjustColumnWidths(table); updateUIState();
    }
    private JComponent createToolbar() {
        var tb = new JPanel(new BorderLayout()); var lBar = new JPanel(new FlowLayout(FlowLayout.LEFT,5,5));
        lBar.add(new JButton("Close"){{addActionListener(_->closeFile());}}); lBar.add(new JButton("Assignment Ops"){{addActionListener(_->runAssignmentOps());}});
        lBar.add(new JLabel("Search:")); lBar.add(searchField);
        var rBar = new JPanel(new FlowLayout(FlowLayout.RIGHT,5,0)); var avgPnl = new JPanel(new FlowLayout(FlowLayout.LEFT,3,0)); avgPnl.add(avgSalLbl);
        avgPnl.add(new JButton("X"){{setMargin(new Insets(1,3,1,3)); addActionListener(_->clearAvgs());}});
        rBar.add(avgPnl); rBar.add(new JButton("Calc Avgs"){{addActionListener(_->calcVisibleAvgs());}});
        advBtn.addActionListener(_->showAdvFilter()); colBtn.addActionListener(_->showColToggle()); rBar.add(advBtn); rBar.add(colBtn);
        tb.add(lBar, BorderLayout.WEST); tb.add(rBar, BorderLayout.EAST); return tb;
    }
    private void runAssignmentOps() { // core assignment logic using streams & function
        Function<Employee, String> formatter = e -> e.name() + " (" + e.department() + ")";
        OptionalDouble avgSal = allEmployees.stream().mapToDouble(Employee::salary).average();
        var txt=new StringBuilder("Assignment Ops Results\n\n1. Average Salary (All): "+(avgSal.isPresent()?String.format("$%,.2f",avgSal.getAsDouble()):"N/A"));
        txt.append("\n2. Employees > 30: ").append(allEmployees.stream().filter(e->e.age()>30).count()).append(" of ").append(allEmployees.size());
        txt.append("\n3. First 10 Employees (Sorted by Name):\n");
        allEmployees.stream().sorted(Comparator.comparing(Employee::name)).limit(10).map(formatter).forEach(s->txt.append("  - ").append(s).append("\n"));
        var ta = new JTextArea(txt.toString(),15,50); ta.setEditable(false); JOptionPane.showMessageDialog(this, new JScrollPane(ta), "Stream API Results",1);
    }
    private void applyFilters() {
        var filters = new ArrayList<RowFilter<Object, Object>>(); String q = searchField.getText().trim();
        if(!q.isEmpty()){ String p = q.startsWith("\"")&&q.endsWith("\"")?"(?i)^"+Pattern.quote(q.substring(1,q.length()-1))+"$":"(?i)"+Pattern.quote(q); var pat=Pattern.compile(p); filters.add(new RowFilter<>(){public boolean include(Entry<?,?>e){ for(int i=0;i<table.getColumnCount();i++) if(e.getValue(table.convertColumnIndexToModel(i))!=null&&pat.matcher(e.getValue(table.convertColumnIndexToModel(i)).toString()).find())return true; return false;}}); }
        if (advFilter != null) filters.add(advFilter);
        sorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
        updateUIState();
    }
    private void showAdvFilter(){ new FilterDialog(this,allHeaders,findCatData(),advFilterState,r->{advFilter=r.filter();advFilterState=r.state();applyFilters();}).setVisible(true); }
    private Map<Integer, List<String>> findCatData(){ if(tblModel.getRowCount()==0)return Map.of(); return IntStream.range(0,allHeaders.length).filter(c->tblModel.getColumnClass(c)==String.class).boxed().collect(Collectors.toMap(c->c, c->IntStream.range(0,tblModel.getRowCount()).mapToObj(r->(String)tblModel.getValueAt(r,c)).filter(Objects::nonNull).map(String::trim).filter(s->!s.isEmpty()).distinct().sorted().toList())).entrySet().stream().filter(e->e.getValue().size()>1&&e.getValue().size()<=15).collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue)); }
    private void showColToggle(){ new ColumnToggleDialog(this, allHeaders, table).setVisible(true); updateUIState(); }
    private void calcVisibleAvgs(){ clearAvgs(); var cIdxs=findCompCols(); if(table.getRowCount()==0||cIdxs.isEmpty()){JOptionPane.showMessageDialog(this,"No data.","Info",1);return;} var d=new StringBuilder("Avg - "); cIdxs.forEach(mI->{int vI=table.convertColumnIndexToView(mI); if(vI!=-1){double avg=IntStream.range(0,table.getRowCount()).mapToDouble(r->{try{Object v=table.getValueAt(r,vI); return v instanceof Number n?n.doubleValue():Double.parseDouble(v.toString().replaceAll("[^\\d.]",""));}catch(Exception e){return 0.0;}}).average().orElse(0.0); table.getColumnModel().getColumn(vI).setCellRenderer(new SalaryCellRenderer(avg));d.append(allHeaders[mI]).append(": ").append(String.format("$%,.2f",avg)).append(" | ");}}); avgSalLbl.setText(d.substring(0,d.length()-3)); table.repaint();}
    private void clearAvgs(){ avgSalLbl.setText("Avg: ---"); for(int i=0;i<table.getColumnCount();i++) if(table.getColumnModel().getColumn(i).getCellRenderer()instanceof SalaryCellRenderer)table.getColumnModel().getColumn(i).setCellRenderer(null); table.repaint();}
    private Set<Integer> findCompCols(){var keys=Set.of("salary","wage","bonus","pay","rate"); return IntStream.range(0,allHeaders.length).filter(i->keys.stream().anyMatch(allHeaders[i].toLowerCase()::contains)&&(tblModel.getRowCount()==0||tblModel.getValueAt(0,i)instanceof Number)).boxed().collect(Collectors.toSet());}
    private void updateUIState(){boolean adv=!advFilterState.isEmpty(),col=table.getColumnCount()!=allHeaders.length;advBtn.setBackground(adv?Color.LIGHT_GRAY:null);advBtn.setOpaque(adv);colBtn.setBackground(col?Color.LIGHT_GRAY:null);colBtn.setOpaque(col); statusLbl.setText(String.format("Showing %d of %d records | %d of %d cols",table.getRowCount(),allEmployees.size(),table.getColumnCount(),allHeaders.length));}
    private void closeFile() { dispose(); SwingUtilities.invokeLater(()->new ImportFrame().setVisible(true)); }
}

@SuppressWarnings("serial")
class ColumnToggleDialog extends JDialog { // column hide/show/reorder management
    private final JTable table; private final DefaultListModel<ColumnState> listModel = new DefaultListModel<>();
    public ColumnToggleDialog(JFrame p, String[] h, JTable tbl) {
        super(p,"Columns",true); this.table=tbl; setLayout(new BorderLayout(10,10));
        Map<Integer,TableColumn> vis=new HashMap<>(); for(int i=0;i<table.getColumnCount();i++) vis.put(table.getColumnModel().getColumn(i).getModelIndex(), table.getColumnModel().getColumn(i));
        IntStream.range(0,h.length).forEach(i->listModel.addElement(new ColumnState(h[i],i,new JCheckBox(h[i],vis.containsKey(i)),vis.getOrDefault(i,new TableColumn(i)))));
        var list=new JList<>(listModel); list.setCellRenderer((l,v,_,s,_)->{var pan=new JPanel(new BorderLayout());pan.add(v.checkBox(),BorderLayout.WEST);pan.setBackground(s?l.getSelectionBackground():l.getBackground());v.checkBox().setBackground(pan.getBackground());return pan;});
        list.addMouseListener(new MouseAdapter(){@Override public void mousePressed(MouseEvent e){int i=list.locationToIndex(e.getPoint()); if(i!=-1&&e.getX()<30) listModel.get(i).checkBox().doClick();}});
        var top=new JPanel(new FlowLayout(FlowLayout.LEFT)); var sAll=new JCheckBox("All", listModel.isEmpty()||IntStream.range(0,listModel.size()).allMatch(i->listModel.get(i).checkBox().isSelected()));
        sAll.addActionListener(_->{boolean s=sAll.isSelected();IntStream.range(0,listModel.size()).forEach(i->listModel.get(i).checkBox().setSelected(s));list.repaint();}); top.add(sAll);
        var listP=new JPanel(new BorderLayout());listP.add(top,BorderLayout.NORTH); listP.add(new JScrollPane(list),BorderLayout.CENTER);
        var moveP=new JPanel(new GridLayout(0,1,5,5)); moveP.add(new JButton("Up"){{addActionListener(_->moveItem(list,-1));}}); moveP.add(new JButton("Down"){{addActionListener(_->moveItem(list,1));}});
        var ctrlP=new JPanel(new FlowLayout(FlowLayout.RIGHT)); ctrlP.add(new JButton("Apply"){{addActionListener(_->apply());}}); ctrlP.add(new JButton("Cancel"){{addActionListener(_->dispose());}});
        add(listP,BorderLayout.CENTER); add(moveP,BorderLayout.EAST); add(ctrlP,BorderLayout.SOUTH); pack(); setSize(Math.max(450,getWidth()),550); setLocationRelativeTo(p);
    }
    private void moveItem(JList<ColumnState> list, int d){int i=list.getSelectedIndex();if(i>-1&&(i+d>=0)&&(i+d<listModel.size())){listModel.add(i+d,listModel.remove(i));list.setSelectedIndex(i+d);}}
    private void apply(){TableColumnModel cm=table.getColumnModel();while(cm.getColumnCount()>0)cm.removeColumn(cm.getColumn(0));IntStream.range(0,listModel.size()).mapToObj(listModel::get).filter(s->s.checkBox().isSelected()).forEach(s->{s.column().setModelIndex(s.modelIndex());s.column().setHeaderValue(s.header());cm.addColumn(s.column());});UIHelper.adjustColumnWidths(table);table.getTableHeader().revalidate();table.repaint();dispose();}
}

@SuppressWarnings("serial")
class FilterDialog extends JDialog { // stateful, reusable filter dialog
    public FilterDialog(JFrame p, String[] h, Map<Integer, List<String>> cM, Map<Integer,Object[]> state, Consumer<FilterResult> onApply) {
        super(p, "Advanced Filter", true); setLayout(new BorderLayout(10,10));
        var grid = new JPanel(new GridLayout(0,2,10,10)) {{setBorder(BorderFactory.createEmptyBorder(10,10,10,10));}};
        var comps = new ArrayList<JComponent>();
        for (int i=0;i<h.length;i++){ grid.add(new JLabel(h[i])); String hdr=h[i].toLowerCase(); if(hdr.contains("age")||hdr.contains("salary")||hdr.contains("bonus")){var mS=new JSpinner(new SpinnerNumberModel(0,0,1000000,hdr.contains("age")?1:1000));var xS=new JSpinner(new SpinnerNumberModel(0,0,1000000,hdr.contains("age")?1:1000));if(state.containsKey(i)){mS.setValue(state.get(i)[0]);xS.setValue(state.get(i)[1]);} var rp=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));mS.setPreferredSize(new Dimension(80,25));xS.setPreferredSize(new Dimension(80,25));rp.add(new JLabel("Min:"));rp.add(mS);rp.add(Box.createHorizontalStrut(10));rp.add(new JLabel("Max:"));rp.add(xS);grid.add(rp);comps.add(mS);comps.add(xS);}else if(cM.containsKey(i)){var c=new JComboBox<>(cM.get(i).toArray(String[]::new));c.insertItemAt("Any",0);c.setSelectedIndex(state.containsKey(i)?(int)state.get(i)[0]:0);grid.add(c);comps.add(c);}else{var tf=new JTextField(15);UIHelper.setTextFieldLimit(tf,50);tf.setText(state.containsKey(i)?(String)state.get(i)[0]:"");grid.add(tf);comps.add(tf);}}
        var bp=new JPanel(new FlowLayout(FlowLayout.RIGHT)); bp.add(new JButton("Clear"){{addActionListener(_->{onApply.accept(new FilterResult(null,Map.of()));dispose();});}}); bp.add(new JButton("Apply"){{addActionListener(_->apply(h,cM,comps,onApply));}});
        var sp=new JScrollPane(grid); sp.getVerticalScrollBar().setUnitIncrement(16); add(sp,BorderLayout.CENTER); add(bp,BorderLayout.SOUTH); pack(); setSize(Math.max(500,getWidth()),Math.min(600,getHeight())); setLocationRelativeTo(p);
    }
    private void apply(String[] h, Map<Integer,List<String>> cM, List<JComponent> cL, Consumer<FilterResult> onApply) {
        var filters=new ArrayList<RowFilter<Object,Object>>(); var state=new HashMap<Integer,Object[]>(); int cIdx=0;
        for (int i=0;i<h.length;i++) { String hdr=h[i].toLowerCase(); if(hdr.contains("age")||hdr.contains("salary")||hdr.contains("bonus")){var m=((JSpinner)cL.get(cIdx++)).getValue();var x=((JSpinner)cL.get(cIdx++)).getValue();if(((Number)m).doubleValue()>0)filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER,(Number)m,i));if(((Number)x).doubleValue()>0)filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.BEFORE,(Number)x,i));if(((Number)m).doubleValue()>0||((Number)x).doubleValue()>0)state.put(i,new Object[]{m,x});} else if(cM.containsKey(i)){var c=(JComboBox<?>)cL.get(cIdx++);if(c.getSelectedIndex()>0){filters.add(RowFilter.regexFilter("^"+Pattern.quote((String)c.getSelectedItem())+"$", i)); state.put(i,new Object[]{c.getSelectedIndex()});}} else{String q=((JTextField)cL.get(cIdx++)).getText().trim();if(!q.isEmpty()){filters.add(RowFilter.regexFilter("(?i)"+Pattern.quote(q),i));state.put(i,new Object[]{q});}}}
        onApply.accept(new FilterResult(filters.isEmpty()?null:RowFilter.andFilter(filters),state)); dispose();
    }
}

@SuppressWarnings("serial")
class SalaryCellRenderer extends DefaultTableCellRenderer { // renders salary cells with color coding
    private final double averageSalary;
    public SalaryCellRenderer(double avg) {this.averageSalary=avg;setOpaque(true);setHorizontalAlignment(SwingConstants.RIGHT);}
    @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean isS, boolean hasF, int r, int c) {
        super.getTableCellRendererComponent(t,v,isS,hasF,r,c); double s=Double.NaN;
        try {s=(v instanceof Number n)?n.doubleValue():Double.parseDouble(v+"".replaceAll("[^\\d.]",""));}catch(Exception ignored){}
        setText(String.format("%,.2f",s)); setBackground(isS?t.getSelectionBackground():(!Double.isNaN(s)?(s>averageSalary?new Color(204,255,204):new Color(255,229,204)):t.getBackground()));
        return this;
    }
}

public class EmployeeDataProcessor { // main entry point
    public static void main(String[] args) { try{UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());}catch(Exception e){e.printStackTrace();} SwingUtilities.invokeLater(()->new ImportFrame().setVisible(true)); }
}