package dev.ingameime.client;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ClientEventsTest {

    @Test
    public void placesPanelAboveInputWhenSpaceIsAvailable() {
        InputBounds panel = ClientEvents.placePanel(new InputBounds(40, 80, 100, 20), 320, 200, 120, 30);

        assertEquals(40, panel.x);
        assertEquals(47, panel.y);
    }

    @Test
    public void placesPanelBelowInputNearTopEdge() {
        InputBounds panel = ClientEvents.placePanel(new InputBounds(40, 5, 100, 20), 320, 200, 120, 30);

        assertEquals(40, panel.x);
        assertEquals(28, panel.y);
    }

    @Test
    public void keepsPanelInsideRightAndBottomEdges() {
        InputBounds panel = ClientEvents.placePanel(new InputBounds(310, 180, 100, 20), 320, 200, 50, 40);

        assertEquals(268, panel.x);
        assertEquals(137, panel.y);
    }
}
