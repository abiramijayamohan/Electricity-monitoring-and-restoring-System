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


    // --------------------------------------------------
    // CONSTRUCTOR
    // --------------------------------------------------

    public DistributionNetwork() {

        areas = new HashMap<>();
        substations = new HashMap<>();
        graph = new HashMap<>();

        faultReports = new ArrayList<>();

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


                // Do not use approved backup links
                // during normal BFS
                if (line.isBackupLink()) {
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

    public void displayBackupLinks() {

        System.out.println(
                "\n--- Approved Alternate Connections ---"
        );


        boolean found = false;


        for (ArrayList<FeederLine> lines :
                graph.values()) {

            for (FeederLine line :
                    lines) {

                // Only display backup links
                if (line.isBackupLink()) {

                    // Prevent duplicate display
                    if (line.getFrom().compareTo(
                            line.getTo()) < 0) {

                        System.out.println(
                                line.getFrom()
                                        + " -> "
                                        + line.getTo()
                                        + " | "
                                        + (line.isWorking()
                                        ? "AVAILABLE"
                                        : "FAILED")
                        );

                        found = true;
                    }
                }
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


                System.out.println(
                        "  -> "
                                + next
                                + " | "
                                + (line.isWorking()
                                ? "WORKING"
                                : "FAILED")
                );
            }
        }

    }
    public void displayApprovedBackupLinks() {

        System.out.println();
        System.out.println("--- Approved Alternate Connections ---");

        System.out.println("Area D -> Area G | AVAILABLE");
        System.out.println("Area B -> Area H | AVAILABLE");
    }
}