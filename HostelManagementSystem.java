
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

/**
 * HOSTEL MANAGEMENT SYSTEM - PORTABLE EDITION
 *
 * Single-file Java Swing application.
 *
 * IMPORTANT:
 * - No MySQL installation is required.
 * - No JDBC driver is required.
 * - No internet connection is required.
 * - All data is stored automatically in hostel_data.dat beside the program.
 *
 * Compile:
 *   javac HostelManagementSystem.java
 *
 * Run:
 *   java HostelManagementSystem
 *
 * Login policy:
 *   Admin   : admin / admin123
 *   Warden  : warden / admin123
 *   Students do NOT have a default password.
 *   A student password can only be assigned/reset by Admin or Warden.
 *
 * This version is intentionally portable so it can be copied to another
 * laptop with Java installed and run without database/service setup.
 */
public class HostelManagementSystem extends JFrame {

    // -------------------- Files / constants --------------------
    static final File DATA_FILE =
            new File(System.getProperty("user.dir"), "hostel_data.dat");

    static final Color NAVY = new Color(27, 55, 79);
    static final Color BLUE = new Color(40, 101, 145);
    static final Color LIGHT_BLUE = new Color(231, 241, 248);
    static final Color BG = new Color(246, 248, 249);
    static final Color CARD = Color.WHITE;
    static final Color TEXT = new Color(31, 42, 48);
    static final Color MUTED = new Color(102, 116, 124);
    static final Color GREEN = new Color(28, 132, 93);
    static final Color ORANGE = new Color(211, 126, 32);
    static final Color RED = new Color(194, 65, 54);
    static final Color BORDER = new Color(220, 226, 229);

    static final Font NORMAL = new Font("Segoe UI", Font.PLAIN, 14);
    static final Font BOLD = NORMAL.deriveFont(Font.BOLD);
    static final Font TITLE = NORMAL.deriveFont(Font.BOLD, 25f);

    static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm");

    static DataStore db;
    User currentUser;

    JPanel content;
    JLabel pageTitle;
    JLabel pageSubtitle;
    JTable mainTable;

    // -------------------- Serializable data model --------------------

    static class DataStore implements Serializable {
        private static final long serialVersionUID = 1L;

        List<User> users = new ArrayList<>();
        List<Room> rooms = new ArrayList<>();
        List<Fee> fees = new ArrayList<>();
        List<Complaint> complaints = new ArrayList<>();
        List<Visitor> visitors = new ArrayList<>();
        List<Allocation> allocations = new ArrayList<>();

        int nextFeeId = 1;
        int nextComplaintId = 1;
        int nextVisitorId = 1;
    }

    static class User implements Serializable {
        private static final long serialVersionUID = 1L;

        String id, name, password, role, phone, course;

        User(String id, String name, String password,
             String role, String phone, String course) {
            this.id = id;
            this.name = name;
            this.password = password;
            this.role = role;
            this.phone = phone;
            this.course = course;
        }
    }

    static class Room implements Serializable {
        private static final long serialVersionUID = 1L;

        String roomNo, block, type;
        int capacity;

        Room(String roomNo, String block, int capacity, String type) {
            this.roomNo = roomNo;
            this.block = block;
            this.capacity = capacity;
            this.type = type;
        }
    }

    static class Allocation implements Serializable {
        private static final long serialVersionUID = 1L;

        String studentId, roomNo, date;

        Allocation(String studentId, String roomNo) {
            this.studentId = studentId;
            this.roomNo = roomNo;
            this.date = LocalDate.now().format(DATE);
        }
    }

    static class Fee implements Serializable {
        private static final long serialVersionUID = 1L;

        int id;
        String studentId, type, date, mode, receipt;
        double amount, paid;

        Fee(int id, String studentId, String type, double amount,
            double paid, String mode, String receipt) {
            this.id = id;
            this.studentId = studentId;
            this.type = type;
            this.amount = amount;
            this.paid = paid;
            this.mode = mode;
            this.receipt = receipt;
            this.date = LocalDate.now().format(DATE);
        }

        double balance() {
            return Math.max(0, amount - paid);
        }
    }

    static class Complaint implements Serializable {
        private static final long serialVersionUID = 1L;

        int id;
        String studentId, category, description, date, status;

        Complaint(int id, String studentId, String category,
                  String description) {
            this.id = id;
            this.studentId = studentId;
            this.category = category;
            this.description = description;
            this.date = LocalDate.now().format(DATE);
            this.status = "Pending";
        }
    }

    static class Visitor implements Serializable {
        private static final long serialVersionUID = 1L;

        int id;
        String studentId, visitorName, relation, phone,
                date, inTime, outTime, status;

        Visitor(int id, String studentId, String visitorName,
                String relation, String phone) {
            this.id = id;
            this.studentId = studentId;
            this.visitorName = visitorName;
            this.relation = relation;
            this.phone = phone;
            this.date = LocalDate.now().format(DATE);
            this.inTime = LocalTime.now().format(TIME);
            this.outTime = "";
            this.status = "IN";
        }
    }

    // -------------------- Persistence --------------------

    static DataStore loadData() {
        if (DATA_FILE.isFile()) {
            try (ObjectInputStream in =
                         new ObjectInputStream(new FileInputStream(DATA_FILE))) {
                DataStore loaded = (DataStore) in.readObject();
                repairData(loaded);
                return loaded;
            } catch (Exception ignored) {
                // A damaged/old file should not stop the application.
                // A clean store is created below.
            }
        }

        DataStore fresh = createDefaultData();
        saveData(fresh);
        return fresh;
    }

    static void saveData() {
        saveData(db);
    }

    static void saveData(DataStore data) {
        try {
            File parent = DATA_FILE.getParentFile();
            if (parent != null) parent.mkdirs();

            File temp = new File(DATA_FILE.getAbsolutePath() + ".tmp");

            try (ObjectOutputStream out =
                         new ObjectOutputStream(new FileOutputStream(temp))) {
                out.writeObject(data);
            }

            if (DATA_FILE.exists() && !DATA_FILE.delete()) {
                throw new IOException("Could not replace old data file.");
            }

            if (!temp.renameTo(DATA_FILE)) {
                throw new IOException("Could not save hostel data.");
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                    null,
                    "The application could not save its local data.\n\n"
                            + e.getMessage(),
                    "Storage Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    static DataStore createDefaultData() {
        DataStore x = new DataStore();

        x.users.add(new User(
                "admin", "Hostel Admin", "admin123",
                "ADMIN", "", ""
        ));

        x.users.add(new User(
                "warden", "Main Warden", "admin123",
                "WARDEN", "9876543210", "Administration"
        ));

        x.users.add(new User(
                "arjun", "Arjun Menon", "",
                "STUDENT", "9841022011", "B.Tech CSE"
        ));

        x.users.add(new User(
                "karthik", "Karthik Raja", "",
                "STUDENT", "9884055123", "B.Com"
        ));

        x.users.add(new User(
                "vignesh", "Vignesh S", "",
                "STUDENT", "9962031877", "MBA"
        ));

        x.users.add(new User(
                "rahul", "Rahul Nair", "",
                "STUDENT", "9840290145", "B.Sc Physics"
        ));

        x.rooms.add(new Room("A-101", "A Block", 4, "Standard"));
        x.rooms.add(new Room("A-102", "A Block", 4, "Standard"));
        x.rooms.add(new Room("B-201", "B Block", 3, "Standard"));
        x.rooms.add(new Room("B-202", "B Block", 3, "Standard"));
        x.rooms.add(new Room("C-301", "C Block", 2, "Premium"));
        x.rooms.add(new Room("C-302", "C Block", 2, "Premium"));

        x.allocations.add(new Allocation("arjun", "A-101"));
        x.allocations.add(new Allocation("karthik", "A-101"));
        x.allocations.add(new Allocation("vignesh", "B-201"));

        x.fees.add(new Fee(
                x.nextFeeId++, "arjun",
                "Hostel Fee", 25000, 25000,
                "UPI", "REC-1001"
        ));

        x.fees.add(new Fee(
                x.nextFeeId++, "karthik",
                "Hostel Fee", 25000, 15000,
                "UPI", "REC-1002"
        ));

        x.fees.add(new Fee(
                x.nextFeeId++, "vignesh",
                "Hostel Fee", 25000, 25000,
                "Cash", "REC-1003"
        ));

        x.complaints.add(new Complaint(
                x.nextComplaintId++,
                "arjun",
                "Maintenance",
                "The ceiling fan in A-101 makes a loud noise."
        ));

        x.visitors.add(new Visitor(
                x.nextVisitorId++,
                "arjun",
                "Suresh Menon",
                "Father",
                "9876543210"
        ));

        return x;
    }

    static void repairData(DataStore x) {
        if (x.users == null) x.users = new ArrayList<>();
        if (x.rooms == null) x.rooms = new ArrayList<>();
        if (x.fees == null) x.fees = new ArrayList<>();
        if (x.complaints == null) x.complaints = new ArrayList<>();
        if (x.visitors == null) x.visitors = new ArrayList<>();
        if (x.allocations == null) x.allocations = new ArrayList<>();

        // Guarantee the required demo accounts exist.
        ensureUser(x, "admin", "Hostel Admin", "admin123",
                "ADMIN", "", "");
        ensureUser(x, "warden", "Main Warden", "admin123",
                "WARDEN", "9876543210", "Administration");
        ensureUser(x, "arjun", "Arjun Menon", "",
                "STUDENT", "9841022011", "B.Tech CSE");

        ensureRoom(x, "A-101", "A Block", 4, "Standard");
        ensureRoom(x, "A-102", "A Block", 4, "Standard");
        ensureRoom(x, "B-201", "B Block", 3, "Standard");
        ensureRoom(x, "B-202", "B Block", 3, "Standard");
        ensureRoom(x, "C-301", "C Block", 2, "Premium");
        ensureRoom(x, "C-302", "C Block", 2, "Premium");

        // Remove orphaned allocations.
        x.allocations.removeIf(a ->
                findUser(x, a.studentId) == null ||
                findRoom(x, a.roomNo) == null
        );

        // A student can have only one room.
        Set<String> seenStudents = new HashSet<>();
        x.allocations.removeIf(a -> !seenStudents.add(a.studentId));

        // Do not allow a room to exceed capacity.
        Map<String, Integer> counts = new HashMap<>();
        Iterator<Allocation> it = x.allocations.iterator();
        while (it.hasNext()) {
            Allocation a = it.next();
            int used = counts.getOrDefault(a.roomNo, 0);
            Room r = findRoom(x, a.roomNo);
            if (r == null || used >= r.capacity) {
                it.remove();
            } else {
                counts.put(a.roomNo, used + 1);
            }
        }

        x.nextFeeId = Math.max(
                x.nextFeeId,
                x.fees.stream().mapToInt(f -> f.id).max().orElse(0) + 1
        );

        x.nextComplaintId = Math.max(
                x.nextComplaintId,
                x.complaints.stream().mapToInt(c -> c.id).max().orElse(0) + 1
        );

        x.nextVisitorId = Math.max(
                x.nextVisitorId,
                x.visitors.stream().mapToInt(v -> v.id).max().orElse(0) + 1
        );

        saveData(x);
    }

    static void ensureUser(DataStore x, String id, String name,
                           String password, String role,
                           String phone, String course) {
        if (findUser(x, id) == null) {
            x.users.add(new User(id, name, password, role, phone, course));
        }
    }

    static void ensureRoom(DataStore x, String no, String block,
                           int capacity, String type) {
        if (findRoom(x, no) == null) {
            x.rooms.add(new Room(no, block, capacity, type));
        }
    }

    static User findUser(DataStore x, String id) {
        for (User u : x.users)
            if (u.id.equalsIgnoreCase(id))
                return u;
        return null;
    }

    static Room findRoom(DataStore x, String roomNo) {
        for (Room r : x.rooms)
            if (r.roomNo.equalsIgnoreCase(roomNo))
                return r;
        return null;
    }

    static int occupied(String roomNo) {
        int n = 0;
        for (Allocation a : db.allocations)
            if (a.roomNo.equalsIgnoreCase(roomNo))
                n++;
        return n;
    }

    static Allocation allocationOf(String studentId) {
        for (Allocation a : db.allocations)
            if (a.studentId.equalsIgnoreCase(studentId))
                return a;
        return null;
    }

    static double totalDue(String studentId) {
        double due = 0;
        for (Fee f : db.fees)
            if (f.studentId.equalsIgnoreCase(studentId))
                due += f.balance();
        return due;
    }

    static double totalFee(String studentId) {
        double total = 0;
        for (Fee f : db.fees)
            if (f.studentId.equalsIgnoreCase(studentId))
                total += f.amount;
        return total;
    }

    static double totalPaid(String studentId) {
        double total = 0;
        for (Fee f : db.fees)
            if (f.studentId.equalsIgnoreCase(studentId))
                total += f.paid;
        return total;
    }

    static User loginUser(String id, String password) {
        for (User u : db.users) {
            if (u.id.equalsIgnoreCase(id)
                    && u.password.equals(password)) {
                return u;
            }
        }
        return null;
    }

    // -------------------- Main window --------------------

    HostelManagementSystem(User u) {
        currentUser = u;

        setTitle("Hostel Management System - " + u.role);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1180, 760);
        setMinimumSize(new Dimension(1000, 650));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        add(buildSidebar(), BorderLayout.WEST);

        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(BG);
        center.add(buildHeader(), BorderLayout.NORTH);

        content = new JPanel(new BorderLayout());
        content.setBackground(BG);
        content.setBorder(new EmptyBorder(0, 28, 28, 28));

        center.add(content, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        openDashboard();
    }

    JPanel buildSidebar() {
        JPanel p = new JPanel();
        p.setPreferredSize(new Dimension(250, 0));
        p.setBackground(NAVY);
        p.setBorder(new EmptyBorder(28, 18, 20, 18));
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));

        JLabel title = label(
                "HOSTEL HUB",
                new Font("Segoe UI", Font.BOLD, 23),
                Color.WHITE
        );
        title.setBorder(new EmptyBorder(0, 12, 3, 0));

        JLabel role = label(
                currentUser.role + " ACCOUNT",
                NORMAL,
                new Color(177, 199, 211)
        );
        role.setBorder(new EmptyBorder(0, 12, 25, 0));

        p.add(title);
        p.add(role);

        addNav(p, "Dashboard", e -> openDashboard(), true);

        if (currentUser.role.equals("ADMIN")
                || currentUser.role.equals("WARDEN")) {

            addNav(p, "Student Management",
                    e -> openStudents(), false);

            addNav(p, "Room Allocation",
                    e -> openRooms(), false);

            addNav(p, "Fee Management",
                    e -> openFees(), false);

            addNav(p, "Complaints",
                    e -> openComplaints(), false);

            addNav(p, "Visitors",
                    e -> openVisitors(), false);

            addNav(p, "Reports",
                    e -> openReports(), false);

        } else {

            addNav(p, "My Room & Status",
                    e -> openMyStatus(), false);

            addNav(p, "My Fees",
                    e -> openMyFees(), false);

            addNav(p, "My Complaints",
                    e -> openComplaints(), false);

            addNav(p, "My Visitors",
                    e -> openVisitors(), false);
        }

        p.add(Box.createVerticalGlue());

        JLabel signed = label(
                "<html>Signed in as<br><b>"
                        + escape(currentUser.name)
                        + "</b></html>",
                NORMAL,
                new Color(205, 218, 225)
        );

        signed.setBorder(new EmptyBorder(0, 12, 12, 0));
        signed.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(signed);

        JButton out = button(
                "Sign out",
                new Color(55, 78, 96),
                Color.WHITE
        );

        out.setAlignmentX(Component.LEFT_ALIGNMENT);
        out.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));

        out.addActionListener(e -> {
            dispose();
            new LoginFrame().setVisible(true);
        });

        p.add(out);

        return p;
    }

    void addNav(JPanel p, String text,
                ActionListener action, boolean active) {
        JButton b = sidebarButton(text, active);
        b.addActionListener(action);
        p.add(b);
        p.add(Box.createVerticalStrut(7));
    }

    JButton sidebarButton(String text, boolean active) {
        JButton b = new JButton(text) {
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();

                g2.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                );

                Color base;

                if (active) {
                    base = Color.WHITE;
                } else if (getModel().isRollover()) {
                    base = new Color(40, 79, 104);
                } else {
                    base = NAVY;
                }

                g2.setColor(base);

                g2.fillRoundRect(
                        0, 0,
                        getWidth() - 1,
                        getHeight() - 1,
                        9, 9
                );

                g2.dispose();
                super.paintComponent(g);
            }
        };

        b.setFont(BOLD);
        b.setForeground(
                active
                        ? new Color(34, 68, 91)
                        : new Color(218, 229, 236)
        );

        b.setBackground(active ? Color.WHITE : NAVY);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setBorder(new EmptyBorder(11, 18, 11, 12));
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setRolloverEnabled(true);
        b.setAlignmentX(Component.LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        b.setCursor(Cursor.getPredefinedCursor(
                Cursor.HAND_CURSOR
        ));

        return b;
    }

    JPanel buildHeader() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG);
        p.setBorder(new EmptyBorder(24, 28, 20, 28));

        JPanel t = new JPanel();
        t.setOpaque(false);
        t.setLayout(new BoxLayout(t, BoxLayout.Y_AXIS));

        pageTitle = label("Dashboard", TITLE, TEXT);
        pageSubtitle = label(
                "Hostel records at a glance",
                NORMAL,
                MUTED
        );

        t.add(pageTitle);
        t.add(pageSubtitle);
        p.add(t, BorderLayout.WEST);

        JLabel date = label(
                LocalDate.now().format(DATE),
                NORMAL,
                MUTED
        );

        p.add(date, BorderLayout.EAST);
        return p;
    }

    void setPage(String title, String subtitle) {
        pageTitle.setText(title);
        pageSubtitle.setText(subtitle);
        content.removeAll();
    }

    // -------------------- Dashboard --------------------

    void openDashboard() {
        setPage(
                "Dashboard",
                "Current hostel occupancy, fees and activity"
        );

        int students = 0;
        for (User u : db.users)
            if (u.role.equals("STUDENT"))
                students++;

        int rooms = db.rooms.size();

        int free = 0;
        for (Room r : db.rooms)
            free += Math.max(0, r.capacity - occupied(r.roomNo));

        int pending = 0;
        for (Complaint c : db.complaints)
            if (!"Resolved".equals(c.status))
                pending++;

        JPanel cards = new JPanel(
                new GridLayout(1, 4, 14, 14)
        );
        cards.setOpaque(false);

        cards.add(stat(
                "Students",
                String.valueOf(students),
                BLUE
        ));

        cards.add(stat(
                "Rooms",
                String.valueOf(rooms),
                NAVY
        ));

        cards.add(stat(
                "Beds Available",
                String.valueOf(free),
                GREEN
        ));

        cards.add(stat(
                "Open Complaints",
                String.valueOf(pending),
                pending > 0 ? RED : GREEN
        ));

        JPanel lower = new JPanel(
                new GridLayout(1, 2, 16, 16)
        );
        lower.setOpaque(false);

        lower.add(buildRecentComplaints());
        lower.add(buildRoomOccupancy());

        JPanel root = new JPanel(
                new BorderLayout(0, 18)
        );
        root.setOpaque(false);

        root.add(cards, BorderLayout.NORTH);
        root.add(lower, BorderLayout.CENTER);

        content.add(root, BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }

    JPanel stat(String title, String value, Color c) {
        JPanel p = card();
        p.setLayout(new BorderLayout());
        p.setBorder(new EmptyBorder(18, 20, 18, 20));

        p.add(
                label(
                        value,
                        new Font("Segoe UI", Font.BOLD, 31),
                        c
                ),
                BorderLayout.NORTH
        );

        p.add(label(title, NORMAL, MUTED), BorderLayout.SOUTH);

        return p;
    }

    JPanel buildRecentComplaints() {
        JPanel p = card();
        p.setLayout(new BorderLayout(0, 10));
        p.setBorder(new EmptyBorder(16, 16, 16, 16));

        p.add(
                label("Recent Complaints", BOLD, TEXT),
                BorderLayout.NORTH
        );

        List<Complaint> list =
                new ArrayList<>(db.complaints);

        list.sort((a, b) -> Integer.compare(b.id, a.id));

        int n = Math.min(6, list.size());
        Object[][] rows = new Object[n][4];

        for (int i = 0; i < n; i++) {
            Complaint c = list.get(i);
            rows[i][0] = c.id;
            rows[i][1] = c.category;
            rows[i][2] = c.status;
            rows[i][3] = c.date;
        }

        JTable t = table(
                rows,
                new String[]{"ID", "Category", "Status", "Date"}
        );

        p.add(new JScrollPane(t), BorderLayout.CENTER);
        return p;
    }

    JPanel buildRoomOccupancy() {
        JPanel p = card();
        p.setLayout(new BorderLayout(0, 10));
        p.setBorder(new EmptyBorder(16, 16, 16, 16));

        p.add(
                label("Room Occupancy", BOLD, TEXT),
                BorderLayout.NORTH
        );

        List<Room> rooms =
                new ArrayList<>(db.rooms);

        rooms.sort(Comparator.comparing(r -> r.roomNo));

        Object[][] rows = new Object[rooms.size()][4];

        for (int i = 0; i < rooms.size(); i++) {
            Room r = rooms.get(i);
            rows[i][0] = r.roomNo;
            rows[i][1] = r.block;
            rows[i][2] = occupied(r.roomNo);
            rows[i][3] = r.capacity;
        }

        JTable t = table(
                rows,
                new String[]{"Room", "Block", "Occupied", "Capacity"}
        );

        p.add(new JScrollPane(t), BorderLayout.CENTER);
        return p;
    }

    // -------------------- Student Management --------------------

    void openStudents() {
        setPage(
                "Student Management",
                "Register, update, allocate and remove hostel residents"
        );

        JPanel top = new JPanel(
                new FlowLayout(FlowLayout.RIGHT, 8, 0)
        );
        top.setOpaque(false);

        JButton add = button(
                "+ Add Student", BLUE, Color.WHITE
        );

        JButton edit = button(
                "Edit", CARD, TEXT
        );

        JButton password = button(
                "Set Login Password", LIGHT_BLUE, BLUE
        );

        JButton remove = button(
                "Remove", CARD, RED
        );

        top.add(edit);
        top.add(password);
        top.add(remove);
        top.add(add);

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);

        head.add(
                label("Resident Records", BOLD, TEXT),
                BorderLayout.WEST
        );

        head.add(top, BorderLayout.EAST);

        Object[][] rows = new Object[0][0];

        List<User> students = new ArrayList<>();

        for (User u : db.users)
            if (u.role.equals("STUDENT"))
                students.add(u);

        students.sort(Comparator.comparing(u -> u.id));

        rows = new Object[students.size()][6];

        for (int i = 0; i < students.size(); i++) {
            User u = students.get(i);
            rows[i][0] = u.id;
            rows[i][1] = u.name;
            rows[i][2] = u.phone;
            rows[i][3] = u.course;

            Allocation a = allocationOf(u.id);
            rows[i][4] = a == null ? "Not allocated" : a.roomNo;
            rows[i][5] = (u.password == null || u.password.isEmpty())
                    ? "Not assigned"
                    : "Assigned";
        }

        mainTable = table(
                rows,
                new String[]{
                        "Student ID",
                        "Name",
                        "Phone",
                        "Course",
                        "Room",
                        "Login"
                }
        );

        add.addActionListener(e -> addStudentDialog());
        edit.addActionListener(e -> editStudent());
        password.addActionListener(e -> assignStudentPassword());
        remove.addActionListener(e -> removeStudent());

        content.add(head, BorderLayout.NORTH);
        content.add(
                new JScrollPane(mainTable),
                BorderLayout.CENTER
        );

        content.revalidate();
        content.repaint();
    }

    void addStudentDialog() {
        JTextField id = new JTextField();
        JTextField name = new JTextField();
        JTextField phone = new JTextField();
        JTextField course = new JTextField();
        JPanel f = form(
                "Student ID", id,
                "Full Name", name,
                "Phone", phone,
                "Course", course
        );

        if (JOptionPane.showConfirmDialog(
                this,
                f,
                "Register Student",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        ) != JOptionPane.OK_OPTION) return;

        String sid = id.getText().trim();

        if (sid.isEmpty()
                || name.getText().trim().isEmpty()) {
            info("Student ID and full name are required.");
            return;
        }

        if (findUser(db, sid) != null) {
            info("That Student ID already exists.");
            return;
        }

        db.users.add(new User(
                sid,
                name.getText().trim(),
                "",
                "STUDENT",
                phone.getText().trim(),
                course.getText().trim()
        ));

        saveData();
        info("Student registered successfully.\n\n"
                + "No login password was created.\n"
                + "Use 'Set Login Password' to assign the student's password.");
        openStudents();
    }

    void assignStudentPassword() {
        int row = selectedRow();
        if (row < 0) return;

        String id = String.valueOf(mainTable.getValueAt(row, 0));
        User u = findUser(db, id);

        if (u == null || !"STUDENT".equals(u.role)) {
            info("Student record not found.");
            return;
        }

        JPasswordField pass1 = new JPasswordField();
        JPasswordField pass2 = new JPasswordField();

        JPanel f = form(
                "New Password", pass1,
                "Confirm Password", pass2
        );

        if (JOptionPane.showConfirmDialog(
                this,
                f,
                "Assign Login Password - " + id,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        ) != JOptionPane.OK_OPTION) return;

        String p1 = new String(pass1.getPassword()).trim();
        String p2 = new String(pass2.getPassword()).trim();

        if (p1.isEmpty()) {
            info("Password cannot be empty.");
            return;
        }

        if (p1.length() < 4) {
            info("Password must contain at least 4 characters.");
            return;
        }

        if (!p1.equals(p2)) {
            info("Passwords do not match.");
            return;
        }

        u.password = p1;
        saveData();

        info("Login password assigned to " + u.name + ".\n\n"
                + "The student can now sign in using their Student ID and this password.");
    }

    int selectedRow() {
        if (mainTable == null) return -1;

        int row = mainTable.getSelectedRow();

        if (row < 0) {
            info("Select a record first.");
            return -1;
        }

        return mainTable.convertRowIndexToModel(row);
    }

    void editStudent() {
        int row = selectedRow();
        if (row < 0) return;

        String id =
                String.valueOf(mainTable.getValueAt(row, 0));

        User u = findUser(db, id);

        if (u == null) {
            info("Student record not found.");
            return;
        }

        JTextField name = new JTextField(u.name);
        JTextField phone = new JTextField(u.phone);
        JTextField course = new JTextField(u.course);

        JPanel f = form(
                "Full Name", name,
                "Phone", phone,
                "Course", course
        );

        if (JOptionPane.showConfirmDialog(
                this,
                f,
                "Edit Student - " + id,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        ) != JOptionPane.OK_OPTION) return;

        u.name = name.getText().trim();
        u.phone = phone.getText().trim();
        u.course = course.getText().trim();

        saveData();
        openStudents();
    }

    void removeStudent() {
        int row = selectedRow();
        if (row < 0) return;

        String id =
                String.valueOf(mainTable.getValueAt(row, 0));

        if (!confirm("Remove student " + id + "?"))
            return;

        db.allocations.removeIf(
                a -> a.studentId.equalsIgnoreCase(id)
        );

        db.fees.removeIf(
                f -> f.studentId.equalsIgnoreCase(id)
        );

        db.complaints.removeIf(
                c -> c.studentId.equalsIgnoreCase(id)
        );

        db.visitors.removeIf(
                v -> v.studentId.equalsIgnoreCase(id)
        );

        db.users.removeIf(
                u -> u.id.equalsIgnoreCase(id)
                        && u.role.equals("STUDENT")
        );

        saveData();
        openStudents();
    }

    // -------------------- Rooms --------------------

    void openRooms() {
        setPage(
                "Room Allocation",
                "View capacity and allocate rooms"
        );

        JPanel top = new JPanel(
                new FlowLayout(FlowLayout.RIGHT)
        );
        top.setOpaque(false);

        JButton allocate = button(
                "Allocate Room",
                BLUE,
                Color.WHITE
        );

        JButton vacate = button(
                "Vacate Room",
                CARD,
                RED
        );

        top.add(vacate);
        top.add(allocate);

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);

        head.add(
                label("Room Status", BOLD, TEXT),
                BorderLayout.WEST
        );

        head.add(top, BorderLayout.EAST);

        List<Room> rooms =
                new ArrayList<>(db.rooms);

        rooms.sort(Comparator.comparing(r -> r.roomNo));

        Object[][] rows =
                new Object[rooms.size()][6];

        for (int i = 0; i < rooms.size(); i++) {
            Room r = rooms.get(i);

            StringBuilder students =
                    new StringBuilder();

            for (Allocation a : db.allocations) {
                if (a.roomNo.equalsIgnoreCase(r.roomNo)) {
                    if (students.length() > 0)
                        students.append(", ");

                    students.append(a.studentId);
                }
            }

            rows[i][0] = r.roomNo;
            rows[i][1] = r.block;
            rows[i][2] = r.type;
            rows[i][3] = occupied(r.roomNo);
            rows[i][4] = r.capacity;
            rows[i][5] =
                    students.length() == 0
                            ? "—"
                            : students.toString();
        }

        mainTable = table(
                rows,
                new String[]{
                        "Room",
                        "Block",
                        "Type",
                        "Occupied",
                        "Capacity",
                        "Students"
                }
        );

        allocate.addActionListener(
                e -> allocateDialog()
        );

        vacate.addActionListener(
                e -> vacateDialog()
        );

        content.add(head, BorderLayout.NORTH);
        content.add(
                new JScrollPane(mainTable),
                BorderLayout.CENTER
        );

        content.revalidate();
        content.repaint();
    }

    void allocateDialog() {
        List<User> students =
                new ArrayList<>();

        for (User u : db.users) {
            if (u.role.equals("STUDENT")
                    && allocationOf(u.id) == null) {
                students.add(u);
            }
        }

        List<Room> rooms =
                new ArrayList<>();

        for (Room r : db.rooms) {
            if (occupied(r.roomNo) < r.capacity)
                rooms.add(r);
        }

        if (students.isEmpty()) {
            info("All students already have rooms.");
            return;
        }

        if (rooms.isEmpty()) {
            info("No beds are available.");
            return;
        }

        JComboBox<String> student =
                new JComboBox<>();

        for (User u : students)
            student.addItem(
                    u.id + " - " + u.name
            );

        JComboBox<String> room =
                new JComboBox<>();

        for (Room r : rooms)
            room.addItem(
                    r.roomNo
                            + " - "
                            + r.block
                            + " ("
                            + occupied(r.roomNo)
                            + "/"
                            + r.capacity
                            + ")"
            );

        JPanel f = form(
                "Student", student,
                "Available Room", room
        );

        if (JOptionPane.showConfirmDialog(
                this,
                f,
                "Room Allocation",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        ) != JOptionPane.OK_OPTION) return;

        String sid =
                student.getSelectedItem()
                        .toString()
                        .split(" - ")[0];

        String roomNo =
                room.getSelectedItem()
                        .toString()
                        .split(" - ")[0];

        if (allocationOf(sid) != null) {
            info("This student already has a room.");
            return;
        }

        Room selectedRoom = findRoom(db, roomNo);

        if (selectedRoom == null) {
            info("Selected room is no longer available. Refresh the Room Allocation page and try again.");
            return;
        }

        int used = occupied(roomNo);

        if (used >= selectedRoom.capacity) {
            info("That room is already full. Please select another available room.");
            openRooms();
            return;
        }

        db.allocations.add(
                new Allocation(sid, roomNo)
        );

        saveData();
        info("Room allocated successfully.");
        openRooms();
    }

    void vacateDialog() {
        List<Allocation> allocations =
                new ArrayList<>(db.allocations);

        if (allocations.isEmpty()) {
            info("No room allocations exist.");
            return;
        }

        JComboBox<String> choice =
                new JComboBox<>();

        for (Allocation a : allocations) {
            User u = findUser(db, a.studentId);

            choice.addItem(
                    a.studentId
                            + " - "
                            + (u == null ? "" : u.name)
                            + " | Room "
                            + a.roomNo
            );
        }

        JPanel f = form("Allocation", choice);

        if (JOptionPane.showConfirmDialog(
                this,
                f,
                "Vacate Room",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        ) != JOptionPane.OK_OPTION) return;

        String sid =
                choice.getSelectedItem()
                        .toString()
                        .split(" - ")[0];

        db.allocations.removeIf(
                a -> a.studentId.equalsIgnoreCase(sid)
        );

        saveData();
        info("Room vacated successfully.");
        openRooms();
    }

    // -------------------- Fees --------------------

    void openFees() {
        if (currentUser.role.equals("STUDENT")) {
            openMyFees();
            return;
        }

        setPage(
                "Fee Management",
                "Hostel and mess fees, payments, balances and receipts"
        );

        JPanel top = new JPanel(
                new FlowLayout(FlowLayout.RIGHT)
        );
        top.setOpaque(false);

        JButton pay = button(
                "Record Payment",
                BLUE,
                Color.WHITE
        );

        top.add(pay);

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);

        head.add(
                label("Fee Ledger", BOLD, TEXT),
                BorderLayout.WEST
        );

        head.add(top, BorderLayout.EAST);

        List<Fee> fees =
                new ArrayList<>(db.fees);

        fees.sort((a, b) ->
                Integer.compare(b.id, a.id)
        );

        Object[][] rows =
                new Object[fees.size()][9];

        for (int i = 0; i < fees.size(); i++) {
            Fee f = fees.get(i);
            User u = findUser(db, f.studentId);

            rows[i][0] = f.id;
            rows[i][1] = f.studentId;
            rows[i][2] =
                    u == null ? "" : u.name;
            rows[i][3] = f.type;
            rows[i][4] = String.format(
                    Locale.US, "%.2f", f.amount
            );
            rows[i][5] = String.format(
                    Locale.US, "%.2f", f.paid
            );
            rows[i][6] = String.format(
                    Locale.US, "%.2f", f.balance()
            );
            rows[i][7] = f.mode;
            rows[i][8] = f.receipt;
        }

        mainTable = table(
                rows,
                new String[]{
                        "ID", "Student", "Name",
                        "Type", "Amount", "Paid",
                        "Balance", "Mode", "Receipt"
                }
        );

        pay.addActionListener(
                e -> paymentDialog()
        );

        content.add(head, BorderLayout.NORTH);
        content.add(
                new JScrollPane(mainTable),
                BorderLayout.CENTER
        );

        content.revalidate();
        content.repaint();
    }

    void paymentDialog() {
        List<User> students = new ArrayList<>();

        for (User u : db.users)
            if (u.role.equals("STUDENT"))
                students.add(u);

        if (students.isEmpty()) {
            info("No students found.");
            return;
        }

        JComboBox<String> student =
                new JComboBox<>();

        for (User u : students)
            student.addItem(
                    u.id + " - " + u.name
            );

        JComboBox<String> type =
                new JComboBox<>(
                        new String[]{
                                "Hostel Fee",
                                "Mess Fee"
                        }
                );

        JTextField amount = new JTextField();

        JComboBox<String> mode =
                new JComboBox<>(
                        new String[]{
                                "Cash",
                                "UPI",
                                "Card",
                                "Bank Transfer"
                        }
                );

        JPanel f = form(
                "Student", student,
                "Fee Type", type,
                "Payment Amount", amount,
                "Mode", mode
        );

        if (JOptionPane.showConfirmDialog(
                this,
                f,
                "Record Payment",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        ) != JOptionPane.OK_OPTION) return;

        try {
            String sid =
                    student.getSelectedItem()
                            .toString()
                            .split(" - ")[0];

            String feeType =
                    type.getSelectedItem().toString();

            double pay =
                    Double.parseDouble(
                            amount.getText().trim()
                    );

            if (pay <= 0)
                throw new IllegalArgumentException(
                        "Payment must be greater than zero."
                );

            Fee existing = null;

            for (Fee fee : db.fees) {
                if (fee.studentId.equalsIgnoreCase(sid)
                        && fee.type.equals(feeType)
                        && fee.balance() > 0) {
                    existing = fee;
                    break;
                }
            }

            String receipt =
                    "REC-"
                            + String.format(
                                    Locale.US,
                                    "%04d",
                                    db.nextFeeId
                            );

            if (existing == null) {
                double defaultAmount =
                        feeType.equals("Hostel Fee")
                                ? 25000
                                : 8000;

                existing = new Fee(
                        db.nextFeeId++,
                        sid,
                        feeType,
                        defaultAmount,
                        0,
                        mode.getSelectedItem()
                                .toString(),
                        receipt
                );

                db.fees.add(existing);
            }

            if (pay > existing.balance()) {
                pay = existing.balance();
            }

            existing.paid += pay;
            existing.mode =
                    mode.getSelectedItem().toString();
            existing.receipt = receipt;
            existing.date =
                    LocalDate.now().format(DATE);

            saveData();

            info(
                    "Payment recorded.\n\n"
                            + "Receipt: "
                            + existing.receipt
                            + "\nAmount: ₹"
                            + String.format(
                                    Locale.US,
                                    "%.2f",
                                    pay
                            )
                            + "\nBalance: ₹"
                            + String.format(
                                    Locale.US,
                                    "%.2f",
                                    existing.balance()
                            )
            );

            openFees();

        } catch (Exception e) {
            error(e);
        }
    }

    // -------------------- Complaints --------------------

    void openComplaints() {
        setPage(
                currentUser.role.equals("STUDENT")
                        ? "My Complaints"
                        : "Complaints",
                currentUser.role.equals("STUDENT")
                        ? "Report a problem and track its status"
                        : "Review and resolve resident complaints"
        );

        JPanel root =
                new JPanel(
                        new BorderLayout(0, 14)
                );
        root.setOpaque(false);

        if (currentUser.role.equals("STUDENT")) {
            JPanel addPanel =
                    new JPanel(
                            new BorderLayout(8, 8)
                    );
            addPanel.setOpaque(false);

            JComboBox<String> category =
                    new JComboBox<>(
                            new String[]{
                                    "Maintenance",
                                    "Food",
                                    "Room",
                                    "Water",
                                    "Electrical",
                                    "Other"
                            }
                    );

            JTextField text =
                    new JTextField();

            JButton send =
                    button(
                            "Submit Complaint",
                            BLUE,
                            Color.WHITE
                    );

            JPanel left =
                    new JPanel(
                            new GridLayout(1, 2, 8, 0)
                    );
            left.setOpaque(false);
            left.add(category);
            left.add(text);

            addPanel.add(left, BorderLayout.CENTER);
            addPanel.add(send, BorderLayout.EAST);

            send.addActionListener(e -> {
                String description =
                        text.getText().trim();

                if (description.isEmpty()) {
                    info("Enter a complaint description.");
                    return;
                }

                db.complaints.add(
                        new Complaint(
                                db.nextComplaintId++,
                                currentUser.id,
                                category.getSelectedItem()
                                        .toString(),
                                description
                        )
                );

                saveData();
                text.setText("");
                openComplaints();
            });

            root.add(addPanel, BorderLayout.NORTH);
        }

        List<Complaint> list =
                new ArrayList<>();

        for (Complaint c : db.complaints) {
            if (currentUser.role.equals("STUDENT")
                    && !c.studentId.equalsIgnoreCase(
                    currentUser.id)) {
                continue;
            }

            list.add(c);
        }

        list.sort((a, b) ->
                Integer.compare(b.id, a.id)
        );

        Object[][] rows =
                new Object[list.size()][7];

        for (int i = 0; i < list.size(); i++) {
            Complaint c = list.get(i);
            User u = findUser(db, c.studentId);

            rows[i][0] = c.id;
            rows[i][1] = c.studentId;
            rows[i][2] =
                    u == null ? "" : u.name;
            rows[i][3] = c.category;
            rows[i][4] = c.description;
            rows[i][5] = c.date;
            rows[i][6] = c.status;
        }

        mainTable = table(
                rows,
                new String[]{
                        "ID", "Student ID", "Student",
                        "Category", "Description",
                        "Date", "Status"
                }
        );

        JPanel actions =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.RIGHT
                        )
                );
        actions.setOpaque(false);

        if (!currentUser.role.equals("STUDENT")) {
            JButton resolve =
                    button(
                            "Mark Resolved",
                            GREEN,
                            Color.WHITE
                    );

            actions.add(resolve);

            resolve.addActionListener(e ->
                    resolveComplaint()
            );
        }

        if (currentUser.role.equals("STUDENT")) {
            root.add(
                    new JScrollPane(mainTable),
                    BorderLayout.CENTER
            );
        } else {
            JPanel body =
                    new JPanel(
                            new BorderLayout()
                    );
            body.setOpaque(false);

            body.add(
                    new JScrollPane(mainTable),
                    BorderLayout.CENTER
            );

            body.add(actions, BorderLayout.SOUTH);

            root.add(body, BorderLayout.CENTER);
        }

        content.add(root, BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }

    void resolveComplaint() {
        int row = selectedRow();
        if (row < 0) return;

        int id =
                Integer.parseInt(
                        mainTable
                                .getValueAt(row, 0)
                                .toString()
                );

        for (Complaint c : db.complaints) {
            if (c.id == id) {
                c.status = "Resolved";
                saveData();
                openComplaints();
                return;
            }
        }
    }

    // -------------------- Visitors --------------------

    void openVisitors() {
        setPage(
                currentUser.role.equals("STUDENT")
                        ? "My Visitors"
                        : "Visitors",
                currentUser.role.equals("STUDENT")
                        ? "Register and manage your visitors"
                        : "Visitor entry and exit records"
        );

        JPanel top =
                new JPanel(
                        new FlowLayout(
                                FlowLayout.RIGHT
                        )
                );
        top.setOpaque(false);

        if (currentUser.role.equals("STUDENT")) {
            JButton add =
                    button(
                            "Register Visitor",
                            BLUE,
                            Color.WHITE
                    );

            JButton checkout =
                    button(
                            "Mark Out",
                            CARD,
                            RED
                    );

            top.add(checkout);
            top.add(add);

            add.addActionListener(
                    e -> visitorDialog()
            );

            checkout.addActionListener(
                    e -> checkoutVisitor()
            );
        }

        List<Visitor> list =
                new ArrayList<>();

        for (Visitor v : db.visitors) {
            if (currentUser.role.equals("STUDENT")
                    && !v.studentId.equalsIgnoreCase(
                    currentUser.id)) {
                continue;
            }

            list.add(v);
        }

        list.sort((a, b) ->
                Integer.compare(b.id, a.id)
        );

        Object[][] rows =
                new Object[list.size()][9];

        for (int i = 0; i < list.size(); i++) {
            Visitor v = list.get(i);
            User u = findUser(db, v.studentId);

            rows[i][0] = v.id;
            rows[i][1] = v.studentId;
            rows[i][2] =
                    u == null ? "" : u.name;
            rows[i][3] = v.visitorName;
            rows[i][4] = v.relation;
            rows[i][5] = v.phone;
            rows[i][6] = v.date;
            rows[i][7] = v.inTime;
            rows[i][8] =
                    v.status.equals("OUT")
                            ? v.outTime
                            : "IN";
        }

        mainTable = table(
                rows,
                new String[]{
                        "ID", "Student ID", "Student",
                        "Visitor", "Relation", "Phone",
                        "Date", "In", "Out / Status"
                }
        );

        JPanel head =
                new JPanel(new BorderLayout());
        head.setOpaque(false);

        head.add(
                label(
                        currentUser.role.equals("STUDENT")
                                ? "Your Visitor Records"
                                : "Visitor Register",
                        BOLD,
                        TEXT
                ),
                BorderLayout.WEST
        );

        head.add(top, BorderLayout.EAST);

        content.add(head, BorderLayout.NORTH);
        content.add(
                new JScrollPane(mainTable),
                BorderLayout.CENTER
        );

        content.revalidate();
        content.repaint();
    }

    void visitorDialog() {
        JTextField name = new JTextField();
        JTextField relation = new JTextField();
        JTextField phone = new JTextField();

        JPanel f = form(
                "Visitor Name", name,
                "Relation", relation,
                "Phone", phone
        );

        if (JOptionPane.showConfirmDialog(
                this,
                f,
                "Register Visitor",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE
        ) != JOptionPane.OK_OPTION) return;

        if (name.getText().trim().isEmpty()) {
            info("Visitor name is required.");
            return;
        }

        db.visitors.add(
                new Visitor(
                        db.nextVisitorId++,
                        currentUser.id,
                        name.getText().trim(),
                        relation.getText().trim(),
                        phone.getText().trim()
                )
        );

        saveData();
        openVisitors();
    }

    void checkoutVisitor() {
        int row = selectedRow();
        if (row < 0) return;

        int id =
                Integer.parseInt(
                        mainTable.getValueAt(
                                row, 0
                        ).toString()
                );

        for (Visitor v : db.visitors) {
            if (v.id == id) {
                if ("OUT".equals(v.status)) {
                    info("Visitor is already marked OUT.");
                    return;
                }

                v.status = "OUT";
                v.outTime =
                        LocalTime.now().format(TIME);

                saveData();
                openVisitors();
                return;
            }
        }
    }

    // -------------------- Student status --------------------

    void openMyStatus() {
        setPage(
                "My Room & Status",
                "Your current hostel allocation and room information"
        );

        Allocation a =
                allocationOf(currentUser.id);

        String roomNo =
                a == null
                        ? "Not allocated"
                        : a.roomNo;

        Room room =
                a == null
                        ? null
                        : findRoom(db, a.roomNo);

        int roommates = 0;

        if (a != null) {
            for (Allocation x : db.allocations) {
                if (x.roomNo.equalsIgnoreCase(a.roomNo)
                        && !x.studentId.equalsIgnoreCase(
                        currentUser.id)) {
                    roommates++;
                }
            }
        }

        JPanel cards =
                new JPanel(
                        new GridLayout(1, 3, 14, 14)
                );
        cards.setOpaque(false);

        cards.add(
                stat("Your Room", roomNo, BLUE)
        );

        cards.add(
                stat(
                        "Roommates",
                        String.valueOf(roommates),
                        NAVY
                )
        );

        cards.add(
                stat(
                        "Fees Due",
                        String.format(
                                Locale.US,
                                "₹%.0f",
                                totalDue(currentUser.id)
                        ),
                        totalDue(currentUser.id) > 0
                                ? RED
                                : GREEN
                )
        );

        JPanel list = card();
        list.setLayout(
                new BoxLayout(
                        list,
                        BoxLayout.Y_AXIS
                )
        );
        list.setBorder(
                new EmptyBorder(
                        20, 22, 20, 22
                )
        );

        list.add(
                label(
                        "Student Information",
                        BOLD,
                        TEXT
                )
        );

        list.add(Box.createVerticalStrut(15));

        addInfoRow(
                list,
                "Name",
                currentUser.name
        );

        addInfoRow(
                list,
                "Student ID",
                currentUser.id
        );

        addInfoRow(
                list,
                "Course",
                currentUser.course
        );

        addInfoRow(
                list,
                "Phone",
                currentUser.phone
        );

        if (room != null) {
            addInfoRow(
                    list,
                    "Block",
                    room.block
            );

            addInfoRow(
                    list,
                    "Room Type",
                    room.type
            );

            addInfoRow(
                    list,
                    "Occupancy",
                    occupied(room.roomNo)
                            + " / "
                            + room.capacity
            );
        }

        JPanel root =
                new JPanel(
                        new BorderLayout(0, 18)
                );
        root.setOpaque(false);

        root.add(cards, BorderLayout.NORTH);
        root.add(list, BorderLayout.CENTER);

        content.add(root, BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }

    void addInfoRow(
            JPanel panel,
            String key,
            String value) {

        JPanel row =
                new JPanel(
                        new BorderLayout()
                );

        row.setOpaque(false);
        row.setBorder(
                new CompoundBorder(
                        new MatteBorder(
                                0, 0, 1, 0,
                                BORDER
                        ),
                        new EmptyBorder(
                                10, 0, 10, 0
                        )
                )
        );

        row.add(
                label(key, BOLD, MUTED),
                BorderLayout.WEST
        );

        row.add(
                label(value, NORMAL, TEXT),
                BorderLayout.EAST
        );

        panel.add(row);
    }

    // -------------------- Student fees --------------------

    void openMyFees() {
        setPage(
                "My Fees",
                "View your hostel and mess fee payments"
        );

        JPanel summary =
                new JPanel(
                        new GridLayout(
                                1, 3, 14, 14
                        )
                );
        summary.setOpaque(false);

        summary.add(
                stat(
                        "Total Fee",
                        String.format(
                                Locale.US,
                                "₹%.0f",
                                totalFee(currentUser.id)
                        ),
                        BLUE
                )
        );

        summary.add(
                stat(
                        "Paid",
                        String.format(
                                Locale.US,
                                "₹%.0f",
                                totalPaid(currentUser.id)
                        ),
                        GREEN
                )
        );

        summary.add(
                stat(
                        "Balance",
                        String.format(
                                Locale.US,
                                "₹%.0f",
                                totalDue(currentUser.id)
                        ),
                        totalDue(currentUser.id) > 0
                                ? RED
                                : GREEN
                )
        );

        List<Fee> list = new ArrayList<>();

        for (Fee f : db.fees)
            if (f.studentId.equalsIgnoreCase(
                    currentUser.id))
                list.add(f);

        Object[][] rows =
                new Object[list.size()][7];

        for (int i = 0; i < list.size(); i++) {
            Fee f = list.get(i);

            rows[i][0] = f.id;
            rows[i][1] = f.type;
            rows[i][2] = f.amount;
            rows[i][3] = f.paid;
            rows[i][4] = f.balance();
            rows[i][5] = f.date;
            rows[i][6] = f.mode;
        }

        JTable t = table(
                rows,
                new String[]{
                        "ID", "Type", "Amount",
                        "Paid", "Balance", "Date",
                        "Payment Mode"
                }
        );

        JPanel root =
                new JPanel(
                        new BorderLayout(0, 18)
                );
        root.setOpaque(false);

        root.add(summary, BorderLayout.NORTH);
        root.add(
                new JScrollPane(t),
                BorderLayout.CENTER
        );

        content.add(root, BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }

    // -------------------- Reports --------------------

    void openReports() {
        setPage(
                "Reports",
                "Hostel summary and financial information"
        );

        int students = 0;

        for (User u : db.users)
            if (u.role.equals("STUDENT"))
                students++;

        int allocated =
                db.allocations.size();

        double feeTotal = 0;
        double paidTotal = 0;
        double balanceTotal = 0;

        for (Fee f : db.fees) {
            feeTotal += f.amount;
            paidTotal += f.paid;
            balanceTotal += f.balance();
        }

        int complaints =
                db.complaints.size();

        int pending = 0;

        for (Complaint c : db.complaints)
            if (!"Resolved".equals(c.status))
                pending++;

        JPanel cards =
                new JPanel(
                        new GridLayout(
                                2, 3, 14, 14
                        )
                );

        cards.setOpaque(false);

        cards.add(
                stat(
                        "Students",
                        String.valueOf(students),
                        BLUE
                )
        );

        cards.add(
                stat(
                        "Allocated",
                        String.valueOf(allocated),
                        NAVY
                )
        );

        cards.add(
                stat(
                        "Total Fee",
                        String.format(
                                Locale.US,
                                "₹%.0f",
                                feeTotal
                        ),
                        BLUE
                )
        );

        cards.add(
                stat(
                        "Collected",
                        String.format(
                                Locale.US,
                                "₹%.0f",
                                paidTotal
                        ),
                        GREEN
                )
        );

        cards.add(
                stat(
                        "Balance",
                        String.format(
                                Locale.US,
                                "₹%.0f",
                                balanceTotal
                        ),
                        balanceTotal > 0
                                ? ORANGE
                                : GREEN
                )
        );

        cards.add(
                stat(
                        "Open Complaints",
                        pending + " / " + complaints,
                        pending > 0
                                ? RED
                                : GREEN
                )
        );

        JPanel note = card();
        note.setLayout(
                new BorderLayout()
        );
        note.setBorder(
                new EmptyBorder(
                        20, 22, 20, 22
                )
        );

        note.add(
                label(
                        "<html><b>Portable project storage</b><br>"
                                + "All records are stored automatically in "
                                + "<b>hostel_data.dat</b> beside the application.<br>"
                                + "You can copy the complete project folder to another "
                                + "Windows laptop with Java installed.</html>",
                        NORMAL,
                        TEXT
                ),
                BorderLayout.NORTH
        );

        JPanel root =
                new JPanel(
                        new BorderLayout(0, 18)
                );
        root.setOpaque(false);

        root.add(cards, BorderLayout.NORTH);
        root.add(note, BorderLayout.CENTER);

        content.add(root, BorderLayout.CENTER);
        content.revalidate();
        content.repaint();
    }

    // -------------------- UI helpers --------------------

    static JTable table(
            Object[][] data,
            String[] cols) {

        DefaultTableModel model =
                new DefaultTableModel(
                        data,
                        cols
                ) {
                    public boolean isCellEditable(
                            int r,
                            int c) {
                        return false;
                    }
                };

        JTable t = new JTable(model);

        t.setRowHeight(38);
        t.setFont(NORMAL);
        t.setForeground(TEXT);
        t.setBackground(Color.WHITE);
        t.setSelectionBackground(LIGHT_BLUE);
        t.setSelectionForeground(TEXT);
        t.setGridColor(BORDER);
        t.setShowHorizontalLines(true);
        t.setShowVerticalLines(false);
        t.setIntercellSpacing(
                new Dimension(0, 1)
        );
        t.setAutoCreateRowSorter(true);
        t.setFillsViewportHeight(true);

        JTableHeader h =
                t.getTableHeader();

        h.setPreferredSize(
                new Dimension(0, 42)
        );

        h.setReorderingAllowed(false);

        h.setDefaultRenderer(
                new DefaultTableCellRenderer() {

                    public Component
                    getTableCellRendererComponent(
                            JTable table,
                            Object value,
                            boolean selected,
                            boolean focused,
                            int row,
                            int column) {

                        JLabel l =
                                (JLabel) super
                                        .getTableCellRendererComponent(
                                                table,
                                                value,
                                                false,
                                                false,
                                                row,
                                                column
                                        );

                        l.setOpaque(true);
                        l.setBackground(
                                new Color(
                                        241, 245, 247
                                )
                        );

                        l.setForeground(
                                new Color(
                                        52, 73, 86
                                )
                        );

                        l.setFont(BOLD);

                        l.setHorizontalAlignment(
                                SwingConstants.LEFT
                        );

                        l.setBorder(
                                new CompoundBorder(
                                        new MatteBorder(
                                                0, 0, 1, 0,
                                                BORDER
                                        ),
                                        new EmptyBorder(
                                                10, 12, 10, 12
                                        )
                                )
                        );

                        return l;
                    }
                }
        );

        t.setDefaultRenderer(
                Object.class,
                new DefaultTableCellRenderer() {

                    public Component
                    getTableCellRendererComponent(
                            JTable table,
                            Object value,
                            boolean selected,
                            boolean focused,
                            int row,
                            int column) {

                        JLabel l =
                                (JLabel) super
                                        .getTableCellRendererComponent(
                                                table,
                                                value,
                                                selected,
                                                false,
                                                row,
                                                column
                                        );

                        l.setFont(NORMAL);
                        l.setForeground(TEXT);

                        l.setBorder(
                                new EmptyBorder(
                                        0, 12, 0, 12
                                )
                        );

                        if (!selected) {
                            l.setBackground(
                                    row % 2 == 0
                                            ? Color.WHITE
                                            : new Color(
                                            249, 251, 252
                                    )
                            );
                        }

                        return l;
                    }
                }
        );

        return t;
    }

    static JPanel form(
            Object... items) {

        JPanel p =
                new JPanel(
                        new GridLayout(
                                0, 2,
                                10, 10
                        )
                );

        p.setBorder(
                new EmptyBorder(
                        8, 8, 8, 8
                )
        );

        for (int i = 0;
             i < items.length;
             i += 2) {

            p.add(
                    label(
                            items[i].toString(),
                            BOLD,
                            TEXT
                    )
            );

            JComponent c =
                    (JComponent) items[i + 1];

            c.setFont(NORMAL);
            p.add(c);
        }

        return p;
    }

    static JLabel label(
            String text,
            Font font,
            Color color) {

        JLabel l =
                new JLabel(text);

        l.setFont(font);
        l.setForeground(color);

        return l;
    }

    static JPanel card() {
        JPanel p =
                new JPanel();

        p.setBackground(CARD);

        p.setBorder(
                new CompoundBorder(
                        new LineBorder(
                                BORDER,
                                1,
                                true
                        ),
                        new EmptyBorder(
                                8, 8, 8, 8
                        )
                )
        );

        return p;
    }

    static JButton button(
            String text,
            Color bg,
            Color fg) {

        JButton b = new JButton(text) {

            protected void paintComponent(
                    Graphics g) {

                Graphics2D g2 =
                        (Graphics2D) g.create();

                g2.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                );

                Color base =
                        getBackground();

                if (getModel().isPressed()) {
                    base = base.darker();
                } else if (getModel().isRollover()
                        && !base.equals(Color.WHITE)) {
                    base = base.brighter();
                }

                g2.setColor(base);

                g2.fillRoundRect(
                        0, 0,
                        getWidth() - 1,
                        getHeight() - 1,
                        9, 9
                );

                if (base.equals(CARD)
                        || base.equals(Color.WHITE)) {

                    g2.setColor(BORDER);

                    g2.drawRoundRect(
                            0, 0,
                            getWidth() - 1,
                            getHeight() - 1,
                            9, 9
                    );
                }

                g2.dispose();

                super.paintComponent(g);
            }
        };

        b.setFont(BOLD);
        b.setForeground(fg);
        b.setBackground(bg);
        b.setFocusPainted(false);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(false);

        b.setBorder(
                new EmptyBorder(
                        10, 16, 10, 16
                )
        );

        b.setCursor(
                Cursor.getPredefinedCursor(
                        Cursor.HAND_CURSOR
                )
        );

        b.setRolloverEnabled(true);

        return b;
    }

    static String escape(String x) {
        return x == null
                ? ""
                : x.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    void info(String text) {
        JOptionPane.showMessageDialog(
                this,
                text,
                "Hostel Management System",
                JOptionPane.INFORMATION_MESSAGE
        );
    }

    void error(Exception e) {
        JOptionPane.showMessageDialog(
                this,
                e.getMessage() == null
                        ? e.toString()
                        : e.getMessage(),
                "Operation Error",
                JOptionPane.ERROR_MESSAGE
        );
    }

    boolean confirm(String text) {
        return JOptionPane.showConfirmDialog(
                this,
                text,
                "Confirm",
                JOptionPane.YES_NO_OPTION
        ) == JOptionPane.YES_OPTION;
    }

    // -------------------- Login --------------------

    static class LoginFrame extends JFrame {

        JTextField id = new JTextField();
        JPasswordField pass =
                new JPasswordField();

        JLabel message =
                label(
                        " ",
                        HostelManagementSystem.NORMAL,
                        RED
                );

        LoginFrame() {
            super(
                    "Hostel Management System - Login"
            );

            setDefaultCloseOperation(
                    EXIT_ON_CLOSE
            );

            setSize(900, 560);
            setLocationRelativeTo(null);
            setResizable(false);
            setLayout(
                    new GridLayout(1, 2)
            );

            JPanel left =
                    new JPanel();

            left.setBackground(NAVY);
            left.setLayout(
                    new BoxLayout(
                            left,
                            BoxLayout.Y_AXIS
                    )
            );

            left.setBorder(
                    new EmptyBorder(
                            145, 50, 30, 30
                    )
            );

            left.add(
                    label(
                            "HOSTEL HUB",
                            new Font(
                                    "Segoe UI",
                                    Font.BOLD,
                                    34
                            ),
                            Color.WHITE
                    )
            );

            left.add(
                    Box.createVerticalStrut(12)
            );

            left.add(
                    label(
                            "Student • Warden • Admin",
                            HostelManagementSystem.NORMAL,
                            new Color(
                                    186, 207, 218
                            )
                    )
            );

            left.add(
                    Box.createVerticalStrut(30)
            );

            left.add(
                    label(
                            "Manage rooms, fees, complaints",
                            HostelManagementSystem.NORMAL,
                            new Color(
                                    186, 207, 218
                            )
                    )
            );

            left.add(
                    label(
                            "visitors and hostel reports.",
                            HostelManagementSystem.NORMAL,
                            new Color(
                                    186, 207, 218
                            )
                    )
            );

            JPanel right =
                    new JPanel(
                            new GridBagLayout()
                    );

            right.setBackground(BG);

            JPanel f =
                    new JPanel();

            f.setOpaque(false);

            f.setLayout(
                    new BoxLayout(
                            f,
                            BoxLayout.Y_AXIS
                    )
            );

            f.setPreferredSize(
                    new Dimension(
                            340, 390
                    )
            );

            f.add(
                    label(
                            "Sign in",
                            TITLE,
                            TEXT
                    )
            );

            f.add(
                    Box.createVerticalStrut(8)
            );

            f.add(
                    label(
                            "Portable hostel account",
                            HostelManagementSystem.NORMAL,
                            MUTED
                    )
            );

            f.add(
                    Box.createVerticalStrut(22)
            );

            f.add(
                    label(
                            "User ID",
                            BOLD,
                            TEXT
                    )
            );

            style(id);
            f.add(id);

            f.add(
                    Box.createVerticalStrut(12)
            );

            f.add(
                    label(
                            "Password",
                            BOLD,
                            TEXT
                    )
            );

            style(pass);
            f.add(pass);

            f.add(
                    Box.createVerticalStrut(12)
            );

            f.add(message);

            f.add(
                    Box.createVerticalStrut(8)
            );

            JButton login =
                    button(
                            "Sign in",
                            BLUE,
                            Color.WHITE
                    );

            login.setAlignmentX(
                    Component.LEFT_ALIGNMENT
            );

            login.addActionListener(
                    e -> doLogin()
            );

            f.add(login);

            f.add(
                    Box.createVerticalStrut(18)
            );

            f.add(
                    label(
                            "Demo: admin / admin123",
                            new Font(
                                    "Segoe UI",
                                    Font.PLAIN,
                                    12
                            ),
                            MUTED
                    )
            );

            f.add(
                    label(
                            "Warden: warden / admin123",
                            new Font(
                                    "Segoe UI",
                                    Font.PLAIN,
                                    12
                            ),
                            MUTED
                    )
            );

            f.add(
                    label(
                            "Student passwords are assigned by Admin/Warden.",
                            new Font(
                                    "Segoe UI",
                                    Font.PLAIN,
                                    12
                            ),
                            MUTED
                    )
            );

            right.add(f);

            add(left);
            add(right);

            getRootPane()
                    .setDefaultButton(login);
        }

        void style(JTextField x) {
            x.setFont(HostelManagementSystem.NORMAL);

            x.setMaximumSize(
                    new Dimension(
                            Integer.MAX_VALUE,
                            42
                    )
            );

            x.setBorder(
                    new CompoundBorder(
                            new LineBorder(
                                    BORDER,
                                    1,
                                    true
                            ),
                            new EmptyBorder(
                                    8, 10, 8, 10
                            )
                    )
            );

            x.setAlignmentX(
                    Component.LEFT_ALIGNMENT
            );
        }

        void doLogin() {
            String uid =
                    id.getText().trim();

            String password =
                    new String(
                            pass.getPassword()
                    );

            if (uid.isEmpty()
                    || password.isEmpty()) {
                message.setText(
                        "Enter user ID and password."
                );
                return;
            }

            User u = findUser(db, uid);

            if (u == null) {
                message.setText(
                        "Invalid ID or password."
                );
                return;
            }

            if ("STUDENT".equals(u.role) && (u.password == null || u.password.isEmpty())) {
                message.setText(
                        "No password has been assigned. Ask the Admin/Warden."
                );
                return;
            }

            if (!u.password.equals(password)) {
                message.setText(
                        "Invalid ID or password."
                );
                return;
            }

            dispose();

            new HostelManagementSystem(u)
                    .setVisible(true);
        }
    }

    // -------------------- Entry point --------------------

    public static void main(String[] args) {

        try {
            UIManager.setLookAndFeel(
                    UIManager
                            .getSystemLookAndFeelClassName()
            );
        } catch (Exception ignored) {
        }

        SwingUtilities.invokeLater(() -> {
            db = loadData();
            new LoginFrame().setVisible(true);
        });
    }
}
