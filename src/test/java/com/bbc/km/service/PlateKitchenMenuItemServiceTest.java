package com.bbc.km.service;

import com.bbc.km.model.ItemStatus;
import com.bbc.km.model.Plate;
import com.bbc.km.model.PlateKitchenMenuItem;
import com.bbc.km.repository.PlateKitchenMenuItemJPARepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class PlateKitchenMenuItemServiceTest {

    private static final String PLATE_ID = "plate-1";

    private PlateKitchenMenuItemJPARepository repository;
    private PlateKitchenMenuItemService service;

    @BeforeEach
    void setup() {
        repository = Mockito.mock(PlateKitchenMenuItemJPARepository.class);
        service = new PlateKitchenMenuItemService(repository, Mockito.mock(PlateService.class), Mockito.mock(StatsService.class));
        ReflectionTestUtils.setField(service, "priorityOrderNotesRegex", "(?i)\\burgente\\b");
        ReflectionTestUtils.invokeMethod(service, "init");
        when(repository.save(any(PlateKitchenMenuItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Plate plate(boolean groupByOrder, int occupied, int capacity) {
        Plate plate = new Plate();
        plate.setId(PLATE_ID);
        plate.setGroupByOrder(groupByOrder);
        plate.setSlot(new ArrayList<>(List.of(occupied, capacity)));
        return plate;
    }

    private PlateKitchenMenuItem item(String id, int orderNumber, ItemStatus status) {
        PlateKitchenMenuItem item = new PlateKitchenMenuItem();
        item.setId(id);
        item.setPlateId(PLATE_ID);
        item.setMenuItemId("menu-" + id);
        item.setOrderNumber(orderNumber);
        item.setTableNumber("1");
        item.setClientName("client");
        item.setStatus(status);
        return item;
    }

    private void givenItems(ItemStatus status, PlateKitchenMenuItem... items) {
        when(repository.findByPlateIdAndStatusOrderByCreatedDateAsc(PLATE_ID, status)).thenReturn(new ArrayList<>(List.of(items)));
        when(repository.countByPlateIdAndStatus(PLATE_ID, status)).thenReturn((long) items.length);
        for (PlateKitchenMenuItem item : items) {
            when(repository.findById(item.getId())).thenReturn(Optional.of(item));
        }
    }

    @Test
    void countOccupiedSlots_withGrouping_countsOneSlotPerOrder() {
        givenItems(ItemStatus.PROGRESS,
                item("a", 10, ItemStatus.PROGRESS), item("b", 10, ItemStatus.PROGRESS),
                item("c", 10, ItemStatus.PROGRESS), item("d", 11, ItemStatus.PROGRESS));

        assertEquals(2, service.countOccupiedSlots(plate(true, 0, 4)));
    }

    @Test
    void countOccupiedSlots_withoutGrouping_countsEveryItem() {
        givenItems(ItemStatus.PROGRESS,
                item("a", 10, ItemStatus.PROGRESS), item("b", 10, ItemStatus.PROGRESS),
                item("c", 11, ItemStatus.PROGRESS));

        assertEquals(3, service.countOccupiedSlots(plate(false, 0, 4)));
    }

    @Test
    void promoteQueuedItems_withGrouping_fullPlateStillTakesItemsOfAnOrderInProgress() {
        PlateKitchenMenuItem inProgress = item("a", 10, ItemStatus.PROGRESS);
        PlateKitchenMenuItem otherOrder = item("b", 11, ItemStatus.TODO);
        PlateKitchenMenuItem sameOrder = item("c", 10, ItemStatus.TODO);
        givenItems(ItemStatus.PROGRESS, inProgress);
        givenItems(ItemStatus.TODO, otherOrder, sameOrder);

        service.promoteQueuedItemsIfPossible(plate(true, 1, 1));

        assertEquals(ItemStatus.TODO, otherOrder.getStatus());
        assertEquals(ItemStatus.PROGRESS, sameOrder.getStatus());
    }

    @Test
    void promoteQueuedItems_withGrouping_promotesAllTheItemsOfAnOrderWithOneSlot() {
        PlateKitchenMenuItem first = item("a", 10, ItemStatus.TODO);
        PlateKitchenMenuItem second = item("b", 10, ItemStatus.TODO);
        PlateKitchenMenuItem otherOrder = item("c", 11, ItemStatus.TODO);
        givenItems(ItemStatus.PROGRESS);
        givenItems(ItemStatus.TODO, first, second, otherOrder);

        service.promoteQueuedItemsIfPossible(plate(true, 0, 1));

        assertEquals(ItemStatus.PROGRESS, first.getStatus());
        assertEquals(ItemStatus.PROGRESS, second.getStatus());
        assertEquals(ItemStatus.TODO, otherOrder.getStatus());
    }

    @Test
    void promoteQueuedItems_withoutGrouping_oneSlotPerItem() {
        PlateKitchenMenuItem first = item("a", 10, ItemStatus.TODO);
        PlateKitchenMenuItem second = item("b", 10, ItemStatus.TODO);
        givenItems(ItemStatus.PROGRESS);
        givenItems(ItemStatus.TODO, first, second);

        service.promoteQueuedItemsIfPossible(plate(false, 0, 1));

        assertEquals(ItemStatus.PROGRESS, first.getStatus());
        assertEquals(ItemStatus.TODO, second.getStatus());
    }
}
