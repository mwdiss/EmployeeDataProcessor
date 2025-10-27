package com.EmployeeDataProcessor;
import javax.swing.*; import javax.swing.event.*; import javax.swing.table.*; import javax.swing.text.*; import java.awt.*; import java.awt.event.*; import java.io.File; import java.nio.charset.Charset;import java.nio.file.Files; import java.util.List; import java.util.stream.*; import java.util.*; import java.util.function.*; import java.util.regex.Pattern; import com.formdev.flatlaf.FlatIntelliJLaf; import java.awt.image.BufferedImage;

//models (compact records for efficient data representation)
record Employee(String name, int age, String department, double salary) {}
record LoadResult(List<Employee> employees, Object[][] tableData, String[] headers) {}
record ColumnState(String header, int modelIndex, JCheckBox checkBox, TableColumn column) {}
record FilterResult(RowFilter<Object, Object> filter, Map<Integer, Object[]> state) {}

class CSVLoader { //parses CSV data with a built-in encoding fallback
    public static LoadResult loadWithFallback(File file, List<Charset> charsetsToTry) throws Exception {
        if (file.length() > 10 * 1024 * 1024) throw new Exception("File size exceeds 10MB limit.");
        List<String> rawLines = null;
        for (Charset charset : charsetsToTry) { try { rawLines = Files.readAllLines(file.toPath(), charset); break; } catch (java.nio.charset.MalformedInputException ignored) {} }
        if (rawLines == null || rawLines.isEmpty()) throw new Exception("Could not decode file. Try a different encoding type.");
        List<String[]> lines = rawLines.stream().map(line -> line.split(",", -1)).toList();
        String[] headers = Arrays.stream(lines.get(0)).map(h -> h.trim().replace("\"", "")).toArray(String[]::new);
        int nameIdx=findHIdx(headers,"name"), ageIdx=findHIdx(headers,"age"), deptIdx=findHIdx(headers,"department","dept"), salIdx=findHIdx(headers,"salary");
        if (nameIdx == -1 && findHIdx(headers, "firstname") == -1 || ageIdx == -1 || deptIdx == -1 || salIdx == -1) throw new Exception("CSV requires: Age, Department, Salary, and Name/FirstName.");
        List<Employee> empList = new ArrayList<>(); Object[][] data = new Object[lines.size() - 1][headers.length];
        for (int i = 1; i < lines.size(); i++) {
            try { String[] p = lines.get(i); String name=nameIdx!=-1?p[nameIdx].trim().replace("\"",""):"N/A", dept=p[deptIdx].trim().replace("\"","");
                empList.add(new Employee(name, Integer.parseInt(p[ageIdx].trim().replace("\"","")), dept, Double.parseDouble(p[salIdx].trim().replace("\"","").replaceAll("[^\\d.]",""))));
                for (int j=0;j<headers.length;j++) { String v=j<p.length?p[j].trim().replace("\"",""):"", h=headers[j].toLowerCase(); if(h.contains("age")&&v.matches("\\d+")) data[i-1][j]=Integer.parseInt(v); else if((h.contains("salary")||h.contains("bonus"))&&!v.isEmpty()) data[i-1][j]=Double.parseDouble(v.replaceAll("[^\\d.]","")); else data[i-1][j]=v; }
            } catch (Exception e) {}
        } return new LoadResult(empList, data, headers);
    } public static int findHIdx(String[] h, String... n) { return IntStream.range(0, h.length).filter(i->Arrays.stream(n).anyMatch(h[i].trim().replace("\"", "").toLowerCase()::contains)).findFirst().orElse(-1); }
}

class UIHelper { //ui helpers to reduce code duplication
    public static void setTextFieldLimit(JTextField f, int l) { ((AbstractDocument)f.getDocument()).setDocumentFilter(new DocumentFilter(){ @Override public void replace(FilterBypass fb, int o, int len, String s, AttributeSet a) throws BadLocationException { if(fb.getDocument().getLength()+(s!=null?s.length():0)-len<=l) super.replace(fb,o,len,s,a); } }); }
    public static void adjustColumnWidths(JTable table) { /*auto-sizes columns based on header and content width*/ table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        for (int c = 0; c < table.getColumnCount(); c++) { TableColumn tc = table.getColumnModel().getColumn(c);
            int w = (int)table.getTableHeader().getDefaultRenderer().getTableCellRendererComponent(table,tc.getHeaderValue(),false,false,-1,c).getPreferredSize().getWidth();
            for (int r = 0; r < Math.min(table.getRowCount(), 50); r++) w = Math.max(w, table.prepareRenderer(table.getCellRenderer(r, c), r, c).getPreferredSize().width); tc.setPreferredWidth(Math.min(w + 15, 400));        }
    }}

@FunctionalInterface interface SimpleDocListener extends DocumentListener { void update(DocumentEvent e);
@Override default void insertUpdate(DocumentEvent e){update(e);} @Override default void removeUpdate(DocumentEvent e){update(e);}
@Override default void changedUpdate(DocumentEvent e){} } //functional interface to simplify listener implementation

class ImportFrame extends JFrame { private static final long serialVersionUID = 1L;
    private final JTextField filePathField = new JTextField(40);
    private final JComboBox<String> encodingBox = new JComboBox<>(new String[]{"UTF-8", "ISO-8859-1", "Windows-1252"});
    public ImportFrame() {
        super("🚀 Employee Data Processor - Import"); setIconImage(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)); setDefaultCloseOperation(EXIT_ON_CLOSE); setSize(550, 240); setLocationRelativeTo(null); setResizable(false);
        var mainPanel = new JPanel(new BorderLayout(10, 10)) {{ setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); }};
        var topPanel = new JPanel(new GridLayout(2, 1, 5, 10));
        var pathPanel = new JPanel(new BorderLayout(5, 0)) {{ add(new JLabel("Dataset Path:"), BorderLayout.WEST); add(filePathField, BorderLayout.CENTER); add(new JButton("📂"){{setPreferredSize(new Dimension(32, 32)); addActionListener(_ -> browseFile());}}, BorderLayout.EAST); }};
        var encodingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT)) {{ add(new JLabel("Encoding:")); add(encodingBox); }};
        topPanel.add(pathPanel); topPanel.add(encodingPanel);
        var info = new JTextArea("Required CSV Columns: Name, Age, Department and Salary.") {{ setEditable(false); setFont(new Font("Sans Serif", Font.ITALIC, 11)); setBackground(getBackground()); }};
        mainPanel.add(topPanel, BorderLayout.NORTH); mainPanel.add(info, BorderLayout.CENTER); mainPanel.add(new JButton("🚀 Import & Launch"){{addActionListener(_ -> importData());}}, BorderLayout.SOUTH); add(mainPanel);
    }
    private void browseFile() { var fd = new FileDialog(this, "Select CSV", FileDialog.LOAD); fd.setFilenameFilter((_, name) -> name.endsWith(".csv")); fd.setVisible(true); if(fd.getFile()!=null) filePathField.setText(fd.getDirectory()+fd.getFile()); }
    private void importData() { //handles the entire file loading and main window launching process
        String path = filePathField.getText();
        if (path.isEmpty()) { JOptionPane.showMessageDialog(this, "Select file.", "Error", 0); return; } setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        try { var charsetsToTry = List.of(Charset.forName((String) encodingBox.getSelectedItem()), Charset.forName("Windows-1252"));
            LoadResult result = CSVLoader.loadWithFallback(new File(path), charsetsToTry); dispose();
            SwingUtilities.invokeLater(() -> new MainFrame(result.employees(), result.headers(), result.tableData(), path).setVisible(true));
        } catch (Exception e) { JOptionPane.showMessageDialog(this, "Failed to load file:\n" + e.getMessage(), "Import Error", 0);
        } finally { setCursor(Cursor.getDefaultCursor()); }
    }}

class MainFrame extends JFrame { private static final long serialVersionUID = 1L;
    private final JTable table; private final DefaultTableModel tblModel; private final TableRowSorter<DefaultTableModel> sorter;
    private final List<Employee> allEmployees; private final String[] allHeaders;
    private final JTextField searchField = new JTextField(20); private RowFilter<Object,Object> advFilter = null;
    private Map<Integer, Object[]> advFilterState = new HashMap<>(); //stores settings for the advanced filter dialog
    private final JButton advBtn = new JButton("🔍 Filter");
    private final JButton colBtn = new JButton("📊 Columns"); private final JLabel statusLbl = new JLabel(), avgSalLbl = new JLabel("Avg: ---");
    public MainFrame(List<Employee> emps, String[] hdrs, Object[][] data, String fName) { //main application window constructor
        super("🏢 Employee Data Processor - "+new File(fName).getName()); setIconImage(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
        allEmployees=emps; allHeaders=hdrs; setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); setSize(1200, 700); setLocationRelativeTo(null); setLayout(new BorderLayout(10,10));
        tblModel = new DefaultTableModel(data, hdrs){ @Override public boolean isCellEditable(int r, int c){return false;} @Override public Class<?> getColumnClass(int c){return getRowCount()>0&&getValueAt(0,c)!=null?getValueAt(0,c).getClass():Object.class;} };
        table = new JTable(tblModel); sorter = new TableRowSorter<>(tblModel); table.setRowSorter(sorter);
        UIHelper.setTextFieldLimit(searchField, 50); searchField.getDocument().addDocumentListener((SimpleDocListener)_ -> applyFilters());
        table.addMouseListener(new MouseAdapter(){ public void mouseClicked(MouseEvent e){
        	if(e.getClickCount()==2&&table.getSelectedRow()!=-1){int r=table.convertRowIndexToModel(table.getSelectedRow()), nIdx=CSVLoader.findHIdx(allHeaders, "name","firstname","lastname"), dIdx=CSVLoader.findHIdx(allHeaders,"department");
        	String n=nIdx!=-1?tblModel.getValueAt(r,nIdx).toString():"?", d=dIdx!=-1?tblModel.getValueAt(r,dIdx).toString():"?"; JOptionPane.showMessageDialog(MainFrame.this,"👤 "+n+" from "+d,"Details",1);}}});
        add(createToolbar(), BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(table); sp.setBorder(BorderFactory.createEtchedBorder()); add(sp, BorderLayout.CENTER);
        JPanel sPnl = new JPanel(new FlowLayout(FlowLayout.LEFT)); sPnl.add(statusLbl); add(sPnl, BorderLayout.SOUTH);
        UIHelper.adjustColumnWidths(table); updateUIState();
    }
    private JComponent createToolbar() { //builds and returns the main application toolbar
        var tb = new JPanel(new BorderLayout()); var lBar = new JPanel(new FlowLayout(FlowLayout.LEFT,5,5));
        lBar.add(new JButton("❌ Close"){{addActionListener(_->closeFile());}}); lBar.add(new JButton("✨ Req Ops"){{addActionListener(_->runAssignmentOps());}});
        lBar.add(new JLabel("Search:")); lBar.add(searchField); var rBar = new JPanel(new FlowLayout(FlowLayout.RIGHT,5,0));
        var avgPnl = new JPanel(new FlowLayout(FlowLayout.LEFT,3,0)); avgPnl.add(avgSalLbl);
        avgPnl.add(new JButton("⌫"){{setMargin(new Insets(1,3,1,3)); addActionListener(_->clearAvgs());}});
        rBar.add(avgPnl); rBar.add(new JButton("💲 Avg"){{addActionListener(_->calcVisibleAvgs());}});
        advBtn.addActionListener(_->showAdvFilter()); colBtn.addActionListener(_->showColToggle()); rBar.add(advBtn); rBar.add(colBtn);tb.add(lBar, BorderLayout.WEST); tb.add(rBar, BorderLayout.EAST); return tb;
    }
    private void runAssignmentOps() { //main assignment logic using streams & function
    	/*1. function interface*/
    	Function<Employee, String> formatter = e -> e.name() + " (" + e.department() + ")";
    	/*4. calculate average salary*/
        OptionalDouble avgSal = allEmployees.stream().mapToDouble(Employee::salary).average();
        var txt=new StringBuilder("Assignment Required Opserations \n\n1. Average Salary (All Employees): "+(avgSal.isPresent()?String.format("$%,.2f",avgSal.getAsDouble()):"N/A"));
        /*3. filter by age*/
        txt.append("\n2. Employees age above 30: ").append(allEmployees.stream().filter(e->e.age()>30).count()).append(" of ").append(allEmployees.size()); txt.append("\n3. First 10 Employees (Sorted by Name):\n");
        /*2. create new collection from stream*/
        allEmployees.stream().sorted(Comparator.comparing(Employee::name)).limit(10).map(formatter).forEach(s->txt.append("  - ").append(s).append("\n"));
        var ta = new JTextArea(txt.toString(),15,50); ta.setEditable(false); JOptionPane.showMessageDialog(this, new JScrollPane(ta), "Stream API Results",1);
    }
    private void applyFilters() { //combines quick search and advanced filters
        var filters = new ArrayList<RowFilter<Object, Object>>(); String q = searchField.getText().trim();
        if (!q.isEmpty()) { String p = q.startsWith("\"") && q.endsWith("\"") && q.length() > 1 ? "(?i)^" + Pattern.quote(q.substring(1, q.length() - 1)) + "$" : "(?i)" + Pattern.quote(q);
            var pat = Pattern.compile(p); filters.add(new RowFilter<>() { public boolean include(Entry<?, ?> e) { for (int i=0; i<table.getColumnCount(); i++) if (e.getValue(table.convertColumnIndexToModel(i)) != null && pat.matcher(e.getValue(table.convertColumnIndexToModel(i)).toString()).find()) return true; return false; }});
        } if (advFilter != null) filters.add(advFilter); sorter.setRowFilter(filters.isEmpty() ? null : RowFilter.andFilter(filters));
        updateUIState();
    }
    private void showAdvFilter(){ new FilterDialog(this,allHeaders,findCatData(),advFilterState,r->{advFilter=r.filter();advFilterState=r.state();applyFilters();}).setVisible(true); }
    /*finds columns with few distinct values for categorical filtering*/
    private Map<Integer, List<String>> findCatData(){ if(tblModel.getRowCount()==0)return Map.of();
    	return IntStream.range(0,allHeaders.length).filter(c->tblModel.getColumnClass(c)==String.class).boxed().collect(Collectors.toMap(c->c, c->IntStream.range(0,tblModel.getRowCount()).mapToObj(r->(String)tblModel.getValueAt(r,c)).filter(Objects::nonNull).map(String::trim).filter(s->!s.isEmpty()).distinct().sorted().toList())).entrySet().stream().filter(e->e.getValue().size()>1&&e.getValue().size()<=15).collect(Collectors.toMap(Map.Entry::getKey,Map.Entry::getValue)); }
    private void showColToggle(){ new ColumnToggleDialog(this, allHeaders, table).setVisible(true); updateUIState(); }
    private void calcVisibleAvgs(){ clearAvgs(); var cIdxs=findCompCols(); if(table.getRowCount()==0||cIdxs.isEmpty()){JOptionPane.showMessageDialog(this,"No data.","Info",1);return;} var d=new StringBuilder("Avg - "); cIdxs.forEach(mI->{int vI=table.convertColumnIndexToView(mI);
    /*calculates average for visible currency columns and applies color renderer*/
    if(vI!=-1){double avg=IntStream.range(0,table.getRowCount()).mapToDouble(r->{
    	try{Object v=table.getValueAt(r,vI); return v instanceof Number n?n.doubleValue():Double.parseDouble(v.toString().replaceAll("[^\\d.]",""));}
    	catch(Exception e){return 0.0;}}).average().orElse(0.0); table.getColumnModel().getColumn(vI).setCellRenderer(new SalaryCellRenderer(avg));d.append(allHeaders[mI]).append(": ").append(String.format("$%,.2f",avg)).append(" | ");}}); avgSalLbl.setText(d.substring(0,d.length()-3)); table.repaint();}
    private void clearAvgs(){ avgSalLbl.setText("Avg: _ _ _"); for(int i=0;i<table.getColumnCount();i++) if(table.getColumnModel().getColumn(i).getCellRenderer()instanceof SalaryCellRenderer)table.getColumnModel().getColumn(i).setCellRenderer(null); table.repaint();}
    /*identifies compensation columns*/
    private Set<Integer> findCompCols(){var keys=Set.of("salary","wage","bonus","pay","rate"); return IntStream.range(0,allHeaders.length).filter(i->keys.stream().anyMatch(allHeaders[i].toLowerCase()::contains)&&(tblModel.getRowCount()==0||tblModel.getValueAt(0,i)instanceof Number)).boxed().collect(Collectors.toSet());}
    /*provides visual feedback for active features*/
    private void updateUIState(){boolean adv=!advFilterState.isEmpty(),col=table.getColumnCount()!=allHeaders.length;advBtn.setBackground(adv?Color.LIGHT_GRAY:null);advBtn.setOpaque(adv);colBtn.setBackground(col?Color.LIGHT_GRAY:null);colBtn.setOpaque(col); statusLbl.setText(String.format("Showing %d of %d records | %d of %d cols",table.getRowCount(),allEmployees.size(),table.getColumnCount(),allHeaders.length));}
    private void closeFile() { dispose(); SwingUtilities.invokeLater(()->new ImportFrame().setVisible(true)); }
}

class ColumnToggleDialog extends JDialog { /*column hide/show/reorder management*/ private static final long serialVersionUID = 1L;
    private final JTable table; private final DefaultListModel<ColumnState> listModel = new DefaultListModel<>();
    public ColumnToggleDialog(JFrame p, String[] h, JTable tbl) { //builds list of all columns
        super(p,"📊 Columns",true); table=tbl; setLayout(new BorderLayout(10,10)); setIconImage(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
        Map<Integer,TableColumn> vis=new HashMap<>(); for(int i=0;i<table.getColumnCount();i++) vis.put(table.getColumnModel().getColumn(i).getModelIndex(), table.getColumnModel().getColumn(i));
        IntStream.range(0,h.length).forEach(i->listModel.addElement(new ColumnState(h[i],i,new JCheckBox(h[i],vis.containsKey(i)),vis.getOrDefault(i,new TableColumn(i)))));
        var list=new JList<>(listModel); list.setCellRenderer((l,v,_,s,_)->{var pan=new JPanel(new BorderLayout());pan.add(v.checkBox(),BorderLayout.WEST);pan.setBackground(s?l.getSelectionBackground():l.getBackground());v.checkBox().setBackground(pan.getBackground());return pan;});
        list.addMouseListener(new MouseAdapter(){@Override public void mousePressed(MouseEvent e){int i=list.locationToIndex(e.getPoint()); if(i!=-1&&e.getX()<30) listModel.get(i).checkBox().doClick();}});
        var top=new JPanel(new FlowLayout(FlowLayout.LEFT)); var sAll=new JCheckBox("All", listModel.isEmpty()||IntStream.range(0,listModel.size()).allMatch(i->listModel.get(i).checkBox().isSelected()));
        sAll.addActionListener(_->{boolean s=sAll.isSelected();IntStream.range(0,listModel.size()).forEach(i->listModel.get(i).checkBox().setSelected(s));list.repaint();}); top.add(sAll);
        var listP=new JPanel(new BorderLayout());listP.add(top,BorderLayout.NORTH); listP.add(new JScrollPane(list),BorderLayout.CENTER);
        var moveP=new JPanel(new GridLayout(0,1,5,5)); moveP.add(new JButton("🔼"){{addActionListener(_->moveItem(list,-1));}}); moveP.add(new JButton("🔽"){{addActionListener(_->moveItem(list,1));}});
        var ctrlP=new JPanel(new FlowLayout(FlowLayout.RIGHT)); ctrlP.add(new JButton("✅ Apply"){{addActionListener(_->apply());}}); ctrlP.add(new JButton("❌ Cancel"){{addActionListener(_->dispose());}});
        add(listP,BorderLayout.CENTER); add(moveP,BorderLayout.EAST); add(ctrlP,BorderLayout.SOUTH); pack(); setSize(Math.max(450,getWidth()),550); setLocationRelativeTo(p);
    }
    private void moveItem(JList<ColumnState> list, int d){int i=list.getSelectedIndex();if(i>-1&&i+d>=0&&i+d<listModel.size()){listModel.add(i+d,listModel.remove(i));list.setSelectedIndex(i+d);}}
    private void apply(){TableColumnModel cm=table.getColumnModel(); while(cm.getColumnCount()>0)cm.removeColumn(cm.getColumn(0));IntStream.range(0,listModel.size()).mapToObj(listModel::get).filter(s->s.checkBox().isSelected()).forEach(s->{s.column().setModelIndex(s.modelIndex());s.column().setHeaderValue(s.header());cm.addColumn(s.column());});UIHelper.adjustColumnWidths(table);table.getTableHeader().revalidate();table.repaint();dispose();} //rebuilds table
}

class FilterDialog extends JDialog { /*stateful, reusable filter dialog*/ private static final long serialVersionUID = 1L;
    public FilterDialog(JFrame p, String[] h, Map<Integer, List<String>> cM, Map<Integer,Object[]> state, Consumer<FilterResult> onApply) { //dynamically builds filter UI and pre-populates
        super(p, "🔍 Advanced Filter", true); setLayout(new BorderLayout(10,10)); setIconImage(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));
        var grid = new JPanel(new GridLayout(0,2,10,10)) {{setBorder(BorderFactory.createEmptyBorder(10,10,10,10));}};
        var comps = new ArrayList<JComponent>();
        for (int i=0;i<h.length;i++){ grid.add(new JLabel(h[i])); String hdr=h[i].toLowerCase();
	        if(hdr.contains("age")||hdr.contains("salary")||hdr.contains("bonus")){var mS=new JSpinner(new SpinnerNumberModel(0,0,1000000,hdr.contains("age")?1:1000));var xS=new JSpinner(new SpinnerNumberModel(0,0,1000000,hdr.contains("age")?1:1000));
	        if(state.containsKey(i)){mS.setValue(state.get(i)[0]);xS.setValue(state.get(i)[1]);}
	        var rp=new JPanel(new FlowLayout(FlowLayout.LEFT,0,0));mS.setPreferredSize(new Dimension(80,25));xS.setPreferredSize(new Dimension(80,25));rp.add(new JLabel("Min:"));rp.add(mS);rp.add(Box.createHorizontalStrut(10));rp.add(new JLabel("Max:"));rp.add(xS);grid.add(rp);comps.add(mS);comps.add(xS);}
	        else if(cM.containsKey(i)){var c=new JComboBox<>(cM.get(i).toArray(String[]::new));c.insertItemAt("Any",0);c.setSelectedIndex(state.containsKey(i)?(int)state.get(i)[0]:0);grid.add(c);comps.add(c);}
	        else{var tf=new JTextField(15);UIHelper.setTextFieldLimit(tf,50);tf.setText(state.containsKey(i)?(String)state.get(i)[0]:"");grid.add(tf);comps.add(tf);}
	    } var bp=new JPanel(new FlowLayout(FlowLayout.RIGHT)); bp.add(new JButton("🗑️ Clear"){{addActionListener(_->{onApply.accept(new FilterResult(null,Map.of()));dispose();});}}); bp.add(new JButton("✅ Apply"){{addActionListener(_->apply(h,cM,comps,onApply));}});
        var sp=new JScrollPane(grid); sp.getVerticalScrollBar().setUnitIncrement(16); add(sp,BorderLayout.CENTER); add(bp,BorderLayout.SOUTH); pack(); setSize(Math.max(500,getWidth()),Math.min(600,getHeight())); setLocationRelativeTo(p);
    }
    private void apply(String[] h, Map<Integer,List<String>> cM, List<JComponent> cL, Consumer<FilterResult> onApply) { //constructs complex filter and saves UI state
        var filters=new ArrayList<RowFilter<Object,Object>>(); var state=new HashMap<Integer,Object[]>(); int cIdx=0;
        for (int i=0;i<h.length;i++) { String hdr=h[i].toLowerCase();
	        if(hdr.contains("age")||hdr.contains("salary")||hdr.contains("bonus")){var m=((JSpinner)cL.get(cIdx++)).getValue();var x=((JSpinner)cL.get(cIdx++)).getValue();
	        if(((Number)m).doubleValue()>0)filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.AFTER,(Number)m,i));
	        if(((Number)x).doubleValue()>0)filters.add(RowFilter.numberFilter(RowFilter.ComparisonType.BEFORE,(Number)x,i));
	        if(((Number)m).doubleValue()>0||((Number)x).doubleValue()>0)state.put(i,new Object[]{m,x});}
	        else if(cM.containsKey(i)){var c=(JComboBox<?>)cL.get(cIdx++);if(c.getSelectedIndex()>0){filters.add(RowFilter.regexFilter("^"+Pattern.quote((String)c.getSelectedItem())+"$", i)); state.put(i,new Object[]{c.getSelectedIndex()});}} else{String q=((JTextField)cL.get(cIdx++)).getText().trim();
	        if(!q.isEmpty()){filters.add(RowFilter.regexFilter("(?i)"+Pattern.quote(q),i));state.put(i,new Object[]{q});}}
	    } onApply.accept(new FilterResult(filters.isEmpty()?null:RowFilter.andFilter(filters),state)); dispose();
    }}

class SalaryCellRenderer extends DefaultTableCellRenderer { private static final long serialVersionUID = 1L;
	//renders salary cells with color coding
    private final double averageSalary;
    public SalaryCellRenderer(double avg) {averageSalary = avg; setOpaque(true); setHorizontalAlignment(SwingConstants.RIGHT);}
    @Override public Component getTableCellRendererComponent(JTable t, Object v, boolean isS, boolean hasF, int r, int c) {
        super.getTableCellRendererComponent(t, v, isS, hasF, r, c); double s = Double.NaN;
        try {s = v instanceof Number n ? n.doubleValue() : Double.parseDouble(v + "".replaceAll("[^\\d.]", ""));} catch (Exception ignored) {} setText(String.format("%,.2f", s));
        if (isS) { setBackground(t.getSelectionBackground()); } else if (!Double.isNaN(s)) {
            if (Math.abs(s - averageSalary) < 0.01) { setBackground(new Color(173, 216, 230)); } //blue for avg
            else { setBackground(s > averageSalary ? new Color(204, 255, 204) : new Color(255, 229, 204)); } /*green > red*/ } else { setBackground(t.getBackground()); } return this;
    }}

public class EmployeeDataProcessor { //main entry point
    public static void main(String[] args) { System.setProperty( "flatlaf.useWindowDecorations", "false" );
        try { FlatIntelliJLaf.setup(); } catch( Exception ex ) { System.err.println( "Failed to initialize LaF" ); }
    	SwingUtilities.invokeLater(()->new ImportFrame().setVisible(true));
}  }