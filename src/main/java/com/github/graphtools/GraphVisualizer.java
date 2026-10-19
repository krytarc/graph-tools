package com.github.graphtools;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Displays a {@link GraphStructure} in a simple Swing window.
 * Vertices are arranged in a circle and edges are labeled when applicable.
 */
public final class GraphVisualizer {
    private static final int NODE_RADIUS = 24;

    private GraphVisualizer() {}

    /**
     * Opens a window for the supplied graph.
     *
     * @param graphStructure graph to render
     * @param name window title
     */
    public static void show(GraphStructure graphStructure, String name) {
        Objects.requireNonNull(graphStructure, "graphStructure must not be null");
        Objects.requireNonNull(name, "name must not be null");
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame(name);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.add(new GraphPanel(graphStructure));
            frame.pack();
            frame.setLocationByPlatform(true);
            frame.setVisible(true);
        });
    }

    private static final class GraphPanel extends JPanel {
        private final GraphStructure graph;

        private GraphPanel(GraphStructure graph) {
            this.graph = graph;
            setPreferredSize(new Dimension(700, 500));
            setBackground(Color.WHITE);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Map<Integer, Point2D.Double> positions = vertexPositions();
            for (Edge edge : graph.getEdges()) {
                Point2D.Double from = positions.get(edge.getU());
                Point2D.Double to = positions.get(edge.getV());
                if (from == null || to == null) {
                    g.dispose();
                    throw new IllegalArgumentException(
                            "Each edge must reference an existing vertex");
                }
                drawEdge(g, edge, from, to);
            }
            for (Vertex vertex : graph.getVertices()) {
                Point2D.Double point = positions.get(vertex.getIndex());
                g.setColor(new Color(225, 238, 252));
                g.fillOval((int) point.x - NODE_RADIUS, (int) point.y - NODE_RADIUS,
                        NODE_RADIUS * 2, NODE_RADIUS * 2);
                g.setColor(new Color(55, 100, 150));
                g.setStroke(new BasicStroke(2));
                g.drawOval((int) point.x - NODE_RADIUS, (int) point.y - NODE_RADIUS,
                        NODE_RADIUS * 2, NODE_RADIUS * 2);
                drawCentered(g, vertex.getLabel().isBlank()
                        ? Integer.toString(vertex.getIndex()) : vertex.getLabel(),
                        (int) point.x, (int) point.y);
            }
            g.dispose();
        }

        private Map<Integer, Point2D.Double> vertexPositions() {
            List<Vertex> vertices = graph.getVertices();
            Map<Integer, Point2D.Double> positions = new HashMap<>();
            double centerX = getWidth() / 2.0;
            double centerY = getHeight() / 2.0;
            double radius = Math.max(40, Math.min(getWidth(), getHeight()) * 0.36);
            for (int i = 0; i < vertices.size(); i++) {
                double angle = -Math.PI / 2 + 2 * Math.PI * i / vertices.size();
                positions.put(vertices.get(i).getIndex(), new Point2D.Double(
                        centerX + radius * Math.cos(angle),
                        centerY + radius * Math.sin(angle)));
            }
            return positions;
        }

        private void drawEdge(Graphics2D g, Edge edge, Point2D.Double from, Point2D.Double to) {
            g.setColor(new Color(90, 90, 90));
            g.setStroke(new BasicStroke(1.5f));
            if (from.equals(to)) {
                g.drawArc((int) from.x - NODE_RADIUS, (int) from.y - NODE_RADIUS * 2,
                        NODE_RADIUS * 2, NODE_RADIUS * 2, 25, 300);
                drawEdgeLabel(g, edge, (int) from.x, (int) from.y - NODE_RADIUS * 2);
                return;
            }

            double dx = to.x - from.x;
            double dy = to.y - from.y;
            double length = Math.hypot(dx, dy);
            double ux = dx / length;
            double uy = dy / length;
            double startX = from.x + ux * NODE_RADIUS;
            double startY = from.y + uy * NODE_RADIUS;
            double endX = to.x - ux * NODE_RADIUS;
            double endY = to.y - uy * NODE_RADIUS;
            g.drawLine((int) startX, (int) startY, (int) endX, (int) endY);

            if (graph.isDirected()) {
                double arrowLength = 10;
                double arrowAngle = Math.PI / 7;
                int x1 = (int) (endX - arrowLength * Math.cos(Math.atan2(dy, dx) - arrowAngle));
                int y1 = (int) (endY - arrowLength * Math.sin(Math.atan2(dy, dx) - arrowAngle));
                int x2 = (int) (endX - arrowLength * Math.cos(Math.atan2(dy, dx) + arrowAngle));
                int y2 = (int) (endY - arrowLength * Math.sin(Math.atan2(dy, dx) + arrowAngle));
                g.drawLine((int) endX, (int) endY, x1, y1);
                g.drawLine((int) endX, (int) endY, x2, y2);
            }
            drawEdgeLabel(g, edge, (int) ((from.x + to.x) / 2),
                    (int) ((from.y + to.y) / 2) - 5);
        }

        private void drawEdgeLabel(Graphics2D g, Edge edge, int x, int y) {
            String label = edge.getLabel();
            if (edge.getWeight() != null && edge.getWeight() != 1.0) {
                label = label.isBlank()
                        ? edge.getWeight().toString()
                        : label + ", " + edge.getWeight();
            }
            if (!label.isBlank()) {
                g.setColor(Color.DARK_GRAY);
                drawCentered(g, label, x, y);
            }
        }

        private void drawCentered(Graphics2D g, String text, int x, int y) {
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(text, x - metrics.stringWidth(text) / 2, y + metrics.getAscent() / 2 - 2);
        }
    }
}
