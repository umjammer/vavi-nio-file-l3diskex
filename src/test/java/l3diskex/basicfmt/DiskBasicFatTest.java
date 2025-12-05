package l3diskex.basicfmt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import l3diskex.basicfmt.type.DiskBasicTypeFAT8;
import l3diskex.diskimg.DiskImage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


class DiskBasicFatTest {

    private DiskBasic mockBasic;
    private DiskBasicType mockType;
    private List<DiskImage.DiskImageSector> sectors;
    private static final int SECTOR_SIZE = 256;
    private static final int FAT_START_POS = 10;
    private static final int FAT_TOTAL_SIZE = 500;

    @BeforeEach
    void setUp() throws IOException {
        // Mock DiskBasic and its dependent type
        mockBasic = mock(DiskBasic.class);
        mockType = mock(DiskBasicTypeFAT8.class); // Use a concrete class that can be mocked

        // --- Configure mockBasic with FAT layout parameters ---
        when(mockBasic.getType()).thenReturn(mockType);
        when(mockBasic.getFatStartSector()).thenReturn(1); // FAT starts at sector 1
        when(mockBasic.getSectorsPerFat()).thenReturn(2); // FAT spans 2 sectors
        when(mockBasic.getFatStartPos()).thenReturn(FAT_START_POS); // FAT starts at a 10-byte offset
        when(mockBasic.getNumberOfFats()).thenReturn(1);
        when(mockBasic.getValidNumberOfFats()).thenReturn(1);
        when(mockBasic.getFatEndGroup()).thenReturn(FAT_TOTAL_SIZE - 1);
        when(mockBasic.getManagedTrackNumber()).thenReturn(0);
        when(mockBasic.getFatSideNumber()).thenReturn(0);
        when(mockBasic.getReversedSideNumber(0)).thenReturn(0);
        when(mockBasic.getSectorSize()).thenReturn(SECTOR_SIZE);
        when(mockBasic.getSectorsPerGroup()).thenReturn(1);

        // --- Create mock sectors with test data ---
        sectors = new ArrayList<>();
        byte[] sector1Data = new byte[SECTOR_SIZE];
        byte[] sector2Data = new byte[SECTOR_SIZE];

        // Fill sectors with recognizable garbage data first
        for (int i = 0; i < SECTOR_SIZE; i++) {
            sector1Data[i] = (byte) 0xEE;
            sector2Data[i] = (byte) 0xFF;
        }

        // Populate the actual FAT data into the sector buffers
        int currentFatPos = 0;
        // Sector 1: from offset 10 to the end
        for (int i = FAT_START_POS; i < SECTOR_SIZE; i++) {
            if (currentFatPos < FAT_TOTAL_SIZE) {
                sector1Data[i] = (byte) (currentFatPos % 256);
                currentFatPos++;
            }
        }
        // Sector 2: from the beginning
        for (int i = 0; i < SECTOR_SIZE; i++) {
            if (currentFatPos < FAT_TOTAL_SIZE) {
                sector2Data[i] = (byte) (currentFatPos % 256);
                currentFatPos++;
            }
        }

        DiskImage.DiskImageSector mockSector1 = mock(DiskImage.DiskImageSector.class);
        when(mockSector1.getSectorBuffer(0)).thenReturn(sector1Data);
        when(mockSector1.getSectorBuffer()).thenReturn(sector1Data);
        when(mockSector1.getSectorSize()).thenReturn(SECTOR_SIZE);

        DiskImage.DiskImageSector mockSector2 = mock(DiskImage.DiskImageSector.class);
        when(mockSector2.getSectorBuffer(0)).thenReturn(sector2Data);
        when(mockSector2.getSectorBuffer()).thenReturn(sector2Data);
        when(mockSector2.getSectorSize()).thenReturn(SECTOR_SIZE);

        sectors.add(mockSector1);
        sectors.add(mockSector2);

        // --- Mock methods that retrieve sectors ---
        when(mockBasic.getSectorFromSectorPos(0, new int[]{0}, new int[]{1})).thenReturn(mockSector1);
        when(mockBasic.getSectorFromSectorPos(1, new int[]{0}, new int[]{1})).thenReturn(mockSector2);

        // Mock getTrack to prevent NullPointerException in DiskBasicFat.assign
        DiskImage.DiskImageTrack mockTrack = mock(DiskImage.DiskImageTrack.class);
        when(mockBasic.getTrack(0, 0)).thenReturn(mockTrack);
        when(mockBasic.getManagedTrack(0, new int[]{0}, new int[]{1})).thenReturn(mockTrack);

    }

    @Test
    @DisplayName("Should correctly read FAT data spanning multiple sectors with an initial offset")
    void assign_ShouldReadFatDataCorrectly_WhenOffsetAndMultiSector() throws IOException {
        // Arrange
        final DiskBasicFat diskBasicFat = new DiskBasicFat(mockBasic);

        // --- Mock type behavior for FAT access ---
        // Let checkFat run against the real data by forwarding calls to a real instance
        DiskBasicTypeFAT8<?> realType = new DiskBasicTypeFAT8<>() {
            @Override
            public boolean isSupported(int typeNumber) {
                return true;
            }
        };
        when(mockType.checkFat(false)).thenAnswer(invocation -> {
            // Initialize the real type with the actual DiskBasicFat instance under test
            realType.init(mockBasic, diskBasicFat, null);
            return realType.checkFat(false);
        });
        when(mockType.getGroupNumber(org.mockito.ArgumentMatchers.anyInt())).thenAnswer(invocation -> {
            int pos = invocation.getArgument(0);
            // Initialize before use to ensure 'fat' field is set
            realType.init(mockBasic, diskBasicFat, null);
            return realType.getGroupNumber(pos);
        });

        // Act
        double validRatio = diskBasicFat.assign(false);

        // Assert
        // 1. Check that assign() and checkFat() consider the FAT valid
        assertTrue(validRatio >= 0.0, "FAT should be considered valid");

        // 2. Verify the content of the FAT data read into the buffers
        DiskBasicFat.DiskBasicFatArea fatArea = diskBasicFat.getDiskBasicFatArea();
        assertNotNull(fatArea, "FatArea should not be null");
        assertEquals(1, fatArea.getValidCount(), "There should be one valid FAT copy");

        // 3. Spot-check the data to ensure offsets were handled correctly
        // The value at each FAT position should be its own index (modulo 256)
        for (int i = 0; i < FAT_TOTAL_SIZE; i++) {
            int expectedValue = i % 256;
            int actualValue = fatArea.getData8(0, i);
            assertEquals(expectedValue, actualValue, "Mismatch at FAT position " + i);
        }
    }
}