import java.util.*;

public class DistributionNetwork {

    // Stores all consumer areas
    private HashMap<String, ConsumerArea> areas;

    // Stores all substations
    private HashMap<String, Substation> substations;

    // Adjacency list for the electricity network
    private HashMap<String, ArrayList<FeederLine>> graph;

    // Stores all fault reports
    private ArrayList<FaultReport> faultReports;

    // Priority queue for repair jobs
    private PriorityQueue<FaultReport> repairQueue;

    // Restoration policy
    private RestorationPolicy restorationPolicy;

    // Stores backup links that have been activated by the operator
    private HashSet<FeederLine> activeBackupLinks;


    // --------------------------------------------------
    // CONSTRUCTOR
    // --------------------------------------------------

    public DistributionNetwork() {

        areas = new HashMap<>();
        substations = new HashMap<>();
        graph = new HashMap<>();

        faultReports = new ArrayList<>();

        // Backup links are inactive when the system starts
        activeBackupLinks = new HashSet<>();


        // Define restoration priority
        restorationPolicy = new RestorationPolicy() {

            @Override
            public Comparator<FaultReport> getComparator() {

                return (a, b) -> {

                    // 1. Critical areas affected
                    // Higher number gets higher priority
                    if (a.getCriticalAreasAffected()
                            != b.getCriticalAreasAffected()) {

                        return Integer.compare(
                                b.getCriticalAreasAffected(),
                                a.getCriticalAreasAffected()
                        );
                    }


                    // 2. Consumers affected
                    // Higher number gets higher priority
                    if (a.getConsumersAffected()
                            != b.getConsumersAffected()) {

                        return Integer.compare(
                                b.getConsumersAffected(),
                                a.getConsumersAffected()
                        );
                    }


                    // 3. Lower report ID first
                    return Integer.compare(
                            a.getReportId(),
                            b.getReportId()
                    );
                };
            }
        };


        // Create priority queue
        repairQueue =
                new PriorityQueue<>(
                        restorationPolicy.getComparator()
                );
    }


    // --------------------------------------------------
    // ADD SUBSTATION
    // --------------------------------------------------

    public void addSubstation(Substation substation) {

        substations.put(
                substation.getId(),
                substation
        );

        graph.put(
                substation.getId(),
                new ArrayList<>()
        );
    }


    // --------------------------------------------------
    // ADD CONSUMER AREA
    // --------------------------------------------------

    public void addArea(ConsumerArea area) {

        areas.put(
                area.getName(),
                area
        );

        graph.put(
                area.getName(),
                new ArrayList<>()
        );
    }


    // --------------------------------------------------
    // ADD FEEDER LINE
    // --------------------------------------------------

    public void addLine(FeederLine line) {

        graph.get(line.getFrom()).add(line);

        graph.get(line.getTo()).add(line);
    }


    // --------------------------------------------------
    // ACTIVATE APPROVED BACKUP LINK
    // --------------------------------------------------

    public void activateBackupLink(FeederLine backupLine) {

        // Check whether the selected line is actually
        // an approved backup link
        if (!backupLine.isBackupLink()) {

            System.out.println(
                    "This is not an approved backup link."
            );

            return;
        }


        // Check whether it has already been activated
        if (activeBackupLinks.contains(backupLine)) {

            System.out.println(
                    "This backup link is already active."
            );

            return;
        }


        // Check whether the backup line itself has failed
        if (!backupLine.isWorking()) {

            System.out.println(
                    "This backup link is currently failed and cannot be activated."
            );

            return;
        }


        // Activate the approved backup connection
        activeBackupLinks.add(backupLine);


        System.out.println(
                "\nApproved backup link activated:"
        );

        System.out.println(
                backupLine.getFrom()
                        + " -> "
                        + backupLine.getTo()
        );


        // Recalculate network connectivity
        System.out.println(
                "\nRecalculating network connectivity..."
        );

        displayAffectedAreas();
    }


    // --------------------------------------------------
    // CHECK WHETHER BACKUP LINK IS ACTIVE
    // --------------------------------------------------

    public boolean isBackupLinkActive(
            FeederLine backupLine) {

        return activeBackupLinks.contains(backupLine);
    }


    // --------------------------------------------------
    // BFS - FIND CONNECTED NODES
    // --------------------------------------------------

    public HashSet<String> findConnectedNodes() {

        HashSet<String> visited =
                new HashSet<>();

        Queue<String> queue =
                new LinkedList<>();


        // Start BFS from active substations
        for (Substation substation :
                substations.values()) {

            if (substation.isActive()) {

                queue.add(
                        substation.getId()
                );

                visited.add(
                        substation.getId()
                );
            }
        }


        // BFS traversal
        while (!queue.isEmpty()) {

            String current =
                    queue.poll();


            // Check all lines connected to current node
            for (FeederLine line :
                    graph.get(current)) {


                // Do not use failed lines
                if (!line.isWorking()) {
                    continue;
                }


                // Backup links are used only when
                // explicitly activated by the operator
                if (line.isBackupLink()
                        && !activeBackupLinks.contains(line)) {

                    continue;
                }


                String next;


                // Find the other end of the line
                if (line.getFrom().equals(current)) {

                    next = line.getTo();

                } else {

                    next = line.getFrom();
                }


                // Visit unvisited node
                if (!visited.contains(next)) {

                    visited.add(next);

                    queue.add(next);
                }
            }
        }


        return visited;
    }


    // --------------------------------------------------
    // DISPLAY AFFECTED AREAS
    // --------------------------------------------------

    public void displayAffectedAreas() {

        HashSet<String> connected =
                findConnectedNodes();


        System.out.println(
                "\nAffected Consumer Areas:"
        );


        boolean found = false;


        for (String areaName :
                areas.keySet()) {

            if (!connected.contains(areaName)) {

                System.out.println(
                        "- " + areaName
                );

                found = true;
            }
        }


        if (!found) {

            System.out.println(
                    "No affected areas."
            );
        }
    }


    // --------------------------------------------------
    // CREATE FAULT REPORT
    // --------------------------------------------------

    public void createFaultReport(
            FeederLine failedLine,
            int repairMinutes) {

        // Find all nodes that are still connected
        HashSet<String> connected =
                findConnectedNodes();


        int criticalAreas = 0;

        int affectedConsumers = 0;


        // Check every consumer area
        for (ConsumerArea area :
                areas.values()) {

            // If BFS cannot reach this area,
            // it is affected.
            if (!connected.contains(area.getName())) {

                // Add its consumers
                affectedConsumers =
                        affectedConsumers
                                + area.getConsumers();


                // CriticalArea has priority 2
                if (area.restorationPriority() == 2) {

                    criticalAreas++;
                }
            }
        }


        // Create a new report ID
        int reportId =
                faultReports.size() + 1;


        // Create the fault report
        FaultReport report =
                new FaultReport(
                        reportId,
                        failedLine,
                        repairMinutes,
                        criticalAreas,
                        affectedConsumers
                );


        // Add report to list and priority queue
        addFaultReport(report);


        // Display report information
        System.out.println(
                "\nFault Report Created!"
        );

        System.out.println(
                "Report ID: FR-"
                        + String.format("%03d", reportId)
        );

        System.out.println(
                "Critical Areas Affected: "
                        + criticalAreas
        );

        System.out.println(
                "Consumers Affected: "
                        + affectedConsumers
        );
    }


    // --------------------------------------------------
    // ADD FAULT REPORT
    // --------------------------------------------------

    public void addFaultReport(
            FaultReport report) {

        faultReports.add(report);

        repairQueue.add(report);
    }


    // --------------------------------------------------
    // FIND FAULT REPORT BY ID
    // --------------------------------------------------

    public FaultReport getFaultReportById(
            int reportId) {

        for (FaultReport report :
                faultReports) {

            if (report.getReportId() == reportId) {

                return report;
            }
        }


        return null;
    }


    // --------------------------------------------------
    // DISPLAY REPAIR PRIORITY QUEUE
    // --------------------------------------------------

    public void displayRepairQueue() {

        System.out.println(
                "\n--- Repair Priority Queue ---"
        );


        if (repairQueue.isEmpty()) {

            System.out.println(
                    "No pending repair jobs."
            );

            return;
        }


        // Temporary queue so original queue
        // is not destroyed
        PriorityQueue<FaultReport> tempQueue =
                new PriorityQueue<>(
                        repairQueue
                );


        while (!tempQueue.isEmpty()) {

            FaultReport report =
                    tempQueue.poll();


            System.out.println(
                    "Report ID: "
                            + report.getReportId()
                            + " | Critical Areas: "
                            + report.getCriticalAreasAffected()
                            + " | Consumers: "
                            + report.getConsumersAffected()
            );
        }
    }


    // --------------------------------------------------
    // ASSIGN REPAIR TEAM
    // --------------------------------------------------

    public void assignRepairTeam(
            FaultReport report,
            RepairTeam team) {


        // Check whether repair is already completed
        if (report.isCompleted()) {

            System.out.println(
                    "This repair is already completed."
            );

            return;
        }


        // Check whether team is available
        if (!team.isAvailable()) {

            System.out.println(
                    team.getTeamName()
                            + " is already busy."
            );

            return;
        }


        // Assign team to report
        report.assignTeam(team);


        System.out.println(
                team.getTeamName()
                        + " assigned to Fault Report "
                        + report.getReportId()
        );
    }


    // --------------------------------------------------
    // COMPLETE REPAIR
    // --------------------------------------------------

    public void completeRepair(
            FaultReport report) {


        // Check whether already completed
        if (report.isCompleted()) {

            System.out.println(
                    "Repair is already completed."
            );

            return;
        }


        // Check whether a team has been assigned
        if (report.getAssignedTeam() == null) {

            System.out.println(
                    "No repair team assigned."
            );

            return;
        }


        // Complete the repair
        report.completeRepair();


        // Remove completed report from priority queue
        repairQueue.remove(report);


        System.out.println(
                "\nRepair completed successfully."
        );


        System.out.println(
                "Line "
                        + report.getFailedLine().getFrom()
                        + " -> "
                        + report.getFailedLine().getTo()
                        + " : WORKING"
        );


        System.out.println(
                report.getAssignedTeam().getTeamName()
                        + " : AVAILABLE"
        );


        // Run BFS again
        System.out.println(
                "\nRecalculating network connectivity..."
        );


        displayAffectedAreas();
    }


    // --------------------------------------------------
    // DISPLAY APPROVED ALTERNATE CONNECTIONS
    // --------------------------------------------------

    public void displayApprovedBackupLinks() {

        System.out.println(
                "\n--- Approved Alternate Connections ---"
        );


        boolean found = false;

        // This HashSet prevents the same backup connection
        // from being displayed more than once.
        HashSet<String> displayedConnections =
                new HashSet<>();


        // Go through all nodes in the graph
        for (ArrayList<FeederLine> lines :
                graph.values()) {

            for (FeederLine line :
                    lines) {

                // Only display backup links
                if (!line.isBackupLink()) {
                    continue;
                }


                // Create a unique key for the connection.
                // Sorting the two endpoint names means:
                // Area D -> Area G
                // and
                // Area G -> Area D
                // are treated as the same connection.
                String node1 = line.getFrom();
                String node2 = line.getTo();

                String connectionKey;

                if (node1.compareTo(node2) < 0) {

                    connectionKey =
                            node1 + " -> " + node2;

                } else {

                    connectionKey =
                            node2 + " -> " + node1;
                }


                // Skip if this connection was already displayed
                if (displayedConnections.contains(connectionKey)) {
                    continue;
                }


                displayedConnections.add(connectionKey);


                String status;


                if (!line.isWorking()) {

                    status = "FAILED";

                } else if (activeBackupLinks.contains(line)) {

                    status = "ACTIVE";

                } else {

                    status = "AVAILABLE";
                }


                // Always display the connection in the
                // original direction stored in FeederLine
                System.out.println(
                        line.getFrom()
                                + " -> "
                                + line.getTo()
                                + " | "
                                + status
                );


                found = true;
            }
        }


        if (!found) {

            System.out.println(
                    "No approved alternate connections."
            );
        }
    }


    // --------------------------------------------------
    // DISPLAY NETWORK
    // --------------------------------------------------

    public void displayNetwork() {

        System.out.println(
                "\n--- Distribution Network ---"
        );


        for (String node :
                graph.keySet()) {


            System.out.println(
                    "\n" + node + ":"
            );


            for (FeederLine line :
                    graph.get(node)) {


                String next;


                if (line.getFrom().equals(node)) {

                    next = line.getTo();

                } else {

                    next = line.getFrom();
                }


                String status;


                if (!line.isWorking()) {

                    status = "FAILED";

                } else if (line.isBackupLink()
                        && activeBackupLinks.contains(line)) {

                    status = "BACKUP ACTIVE";

                } else if (line.isBackupLink()) {

                    status = "BACKUP AVAILABLE";

                } else {

                    status = "WORKING";
                }


                System.out.println(
                        "  -> "
                                + next
                                + " | "
                                + status
                );
            }
        }
    }
}