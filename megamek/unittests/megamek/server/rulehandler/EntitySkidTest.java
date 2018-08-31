package megamek.server.rulehandler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import megamek.common.Building;
import megamek.common.Coords;
import megamek.common.Crew;
import megamek.common.Entity;
import megamek.common.EntityMovementMode;
import megamek.common.EntityMovementType;
import megamek.common.Game;
import megamek.common.Hex;
import megamek.common.IBoard;
import megamek.common.IGame;
import megamek.common.IHex;
import megamek.common.IPlayer;
import megamek.common.MoveStep;
import megamek.common.PilotingRollData;
import megamek.common.Tank;
import megamek.common.Terrain;
import megamek.common.Terrains;
import megamek.common.net.Packet;
import megamek.common.options.GameOptions;
import megamek.common.options.PilotOptions;
import megamek.server.Server;

public class EntitySkidTest {
    
    private Coords curPos;
    private IHex curHex;
    private IHex nextHex;
    private Entity entity;
    private IBoard board;
    private EntitySkid skid;
    private IGame game;
    private Server server;
    private List<RuleHandler> childHandlers;
    
    private void initSkid(int elevHex1, int elevHex2) {
        initSkid(elevHex1, elevHex2, EntityMovementMode.BIPED, 0);
    }
    
    private void initSkid(int elevHex1, int elevHex2, EntityMovementMode movementMode, int startElev) {
        initSkid(elevHex1, elevHex2, movementMode, startElev, mock(Entity.class));
    }

    private void initSkid(int elevHex1, int elevHex2, EntityMovementMode movementMode, int startElev, Entity entity) {
        this.entity = entity;
        when(entity.getMovementMode()).thenReturn(movementMode);
        game = new Game();
        game.addEntity(entity, false);
        curPos = new Coords(0, 0);
        curHex = new Hex();
        curHex.setLevel(elevHex1);
        nextHex = new Hex();
        nextHex.setLevel(elevHex2);
        board = mock(IBoard.class);
        when(board.getHex(any(Coords.class)))
            .thenAnswer(inv -> curPos.equals(inv.getArguments()[0])? curHex : nextHex);
        when(board.contains(any(Coords.class))).thenReturn(true);
        game.setBoard(board);
        server = mock(Server.class);
        childHandlers = new ArrayList<>();
        skid = new EntitySkid(entity, curPos, startElev, 0, 5, mock(MoveStep.class),
                EntityMovementType.MOVE_WALK, false, server) {
            @Override
            public void process(RuleHandler child, IGame game) {
                childHandlers.add(child);
            }
        };
        skid.initStartingValues(game);
    }
    
    /**
     * @return A {@link Crew} mock that returns false for boolean SPAs
     */
    private Crew createMockCrew() {
        PilotOptions emptyOptions = mock(PilotOptions.class);
        when(emptyOptions.booleanOption(anyString())).thenReturn(false);
        Crew crew = mock(Crew.class);
        when(crew.getOptions()).thenReturn(emptyOptions);
        return crew;
    }
    
    private Entity createMockEntity(final int id) {
        Entity entity = mock(Entity.class);
        when(entity.getId()).thenReturn(id);
        IPlayer owner = mock(IPlayer.class);
        when(owner.getColorIndex()).thenReturn(1);
        when(entity.getOwner()).thenReturn(owner);
        return entity;
    }
    
    @Test
    public void skidOffMapRemovesEntityAndCarriedWithPushOffBoardOption() {
        Entity entity = createMockEntity(1);
        IGame game = new Game();
        game.addEntity(entity, false);
        Entity loaded = createMockEntity(2);
        when(entity.getLoadedUnits()).thenReturn(Collections.singletonList(loaded));
        game.addEntity(loaded, false);
        Entity swarmer = createMockEntity(3);
        when(entity.getSwarmAttackerId()).thenReturn(3);
        game.addEntity(swarmer, false);
        IBoard board = mock(IBoard.class);
        when(board.contains(any(Coords.class))).thenReturn(false);
        GameOptions options = mock(GameOptions.class);
        when(options.booleanOption(anyString())).thenReturn(true);
        game.setOptions(options);
        game.setBoard(board);
        
        EntitySkid skid = new EntitySkid(entity, curPos, 0, 0, 5, mock(MoveStep.class),
                EntityMovementType.MOVE_WALK, false, mock(Server.class));
        skid.checkSkidOffMap(game);
        
        assertFalse(game.getEntitiesVector().contains(entity));
        assertFalse(game.getEntitiesVector().contains(loaded));
        assertFalse(game.getEntitiesVector().contains(swarmer));
        assertEquals(skid.getPackets().stream()
                .filter(p -> p.getCommand() == Packet.COMMAND_ENTITY_REMOVE).count(), 3);
    }

    @Test
    public void skidOffMapStopsSkidWithoutPushOffBoardOption() {
        Entity entity = createMockEntity(1);
        IGame game = new Game();
        game.addEntity(entity, false);
        Entity loaded = createMockEntity(2);
        when(entity.getLoadedUnits()).thenReturn(Collections.singletonList(loaded));
        game.addEntity(loaded, false);
        Entity swarmer = createMockEntity(3);
        when(entity.getSwarmAttackerId()).thenReturn(3);
        game.addEntity(swarmer, false);
        IBoard board = mock(IBoard.class);
        when(board.contains(any(Coords.class))).thenReturn(false);
        GameOptions options = mock(GameOptions.class);
        when(options.booleanOption(anyString())).thenReturn(false);
        game.setOptions(options);
        game.setBoard(board);
        
        EntitySkid skid = new EntitySkid(entity, curPos, 0, 0, 5, mock(MoveStep.class),
                EntityMovementType.MOVE_WALK, false, mock(Server.class));
        skid.checkSkidOffMap(game);
        
        assertTrue(game.getEntitiesVector().contains(entity));
        assertTrue(game.getEntitiesVector().contains(loaded));
        assertTrue(game.getEntitiesVector().contains(swarmer));
    }

    @Test
    public void entityDropsToLowerHex() {
        initSkid(2, 1);
        skid.initStartingValues(game);

        skid.updatePosition(game);

        assertEquals(nextHex.floor(), skid.getNextAltitude());
    }

    @Test
    public void vtolMaintainsElevation() {
        initSkid(2, 1, EntityMovementMode.VTOL, 5);

        skid.updatePosition(game);

        assertEquals(skid.getCurrentElevation() + curHex.floor(), skid.getNextAltitude());
    }
    
    @Test
    public void wigeRemainsAboveSurface() {
        initSkid(2, 1, EntityMovementMode.WIGE, 1);

        skid.updatePosition(game);

        assertEquals(nextHex.floor() + 1, skid.getNextAltitude());
    }
    
    @Test
    public void groundedWIGEDropsToSurface() {
        initSkid(2, 1, EntityMovementMode.WIGE, 0);

        skid.updatePosition(game);

        assertEquals(nextHex.floor(), skid.getNextAltitude());
    }

    @Test
    public void entityDropsToRoofOfBuilding() {
        initSkid(5, 1);
        nextHex.addTerrain(new Terrain(Terrains.BLDG_ELEV, 1));

        skid.updatePosition(game);

        assertEquals(nextHex.ceiling(), skid.getNextAltitude());
    }

    @Test
    public void entityDropsToBridgeWithCorrectExitDirection() {
        initSkid(5, 1);
        nextHex.addTerrain(new Terrain(Terrains.BRIDGE, 1, true, (1 << 3)));

        skid.updatePosition(game);

        assertEquals(nextHex.ceiling(), skid.getNextAltitude());
    }

    @Test
    public void entityMissesBridgeWithOtherExitDirection() {
        initSkid(5, 1);
        nextHex.addTerrain(new Terrain(Terrains.BRIDGE, 1, true, (1)));

        skid.updatePosition(game);

        assertEquals(nextHex.floor(), skid.getNextAltitude());
    }

    @Test
    public void entityMissesHigherBridge() {
        initSkid(5, 1);
        nextHex.addTerrain(new Terrain(Terrains.BRIDGE, 3, true, (1 << 3)));
        
        skid.updatePosition(game);

        assertEquals(nextHex.floor(), skid.getNextAltitude());
    }

    @Test
    public void hoverTankSkidsAcrossWater() {
        initSkid(2, 1, EntityMovementMode.HOVER, 0, mock(Tank.class));
        nextHex.addTerrain(new Terrain(Terrains.WATER, 3));

        skid.updatePosition(game);

        assertEquals(nextHex.surface(), skid.getNextAltitude());
        assertTrue(nextHex.floor() < skid.getNextAltitude());
    }

    @Test
    public void nonHoverTankSkidsIntoWater() {
        initSkid(2, 1);
        nextHex.addTerrain(new Terrain(Terrains.WATER, 3));

        skid.updatePosition(game);

        assertEquals(nextHex.floor(), skid.getNextAltitude());
        assertTrue(nextHex.surface() > skid.getNextAltitude());
    }

    @Test
    public void entitySkidsAcrossIce() {
        initSkid(2, 1);
        nextHex.addTerrain(new Terrain(Terrains.ICE, 3));

        skid.updatePosition(game);

        assertEquals(nextHex.surface(), skid.getNextAltitude());
    }

    @Test
    public void entityCrashesIntoHigherHex() {
        initSkid(2, 3);
        
        skid.updatePosition(game);
        
        assertTrue(skid.checkTerrainCollision(game));
    }

    @Test
    public void vtolCrashesIntoWoodsHex() {
        final int STARTING_ELEV = 1;
        initSkid(0, 0, EntityMovementMode.VTOL, STARTING_ELEV);
        nextHex.addTerrain(new Terrain(Terrains.WOODS, 1)); // light woods
        Crew crew = createMockCrew();
        when(entity.getCrew()).thenReturn(crew); // needed to pass movement cost calculation
        
        skid.updatePosition(game);
        
        assertTrue(skid.checkTerrainCollision(game));
    }

    @Test
    public void vtolSkidsOverWoodsHex() {
        final int STARTING_ELEV = 2;
        initSkid(0, 0, EntityMovementMode.VTOL, STARTING_ELEV);
        nextHex.addTerrain(new Terrain(Terrains.WOODS, 1)); // light woods
        Crew crew = createMockCrew();
        when(entity.getCrew()).thenReturn(crew); // needed to pass movement cost calculation
        
        skid.updatePosition(game);
        
        assertFalse(skid.checkTerrainCollision(game));
    }

    @Test
    public void entityCrashesIntoWallFromAbove() {
        initSkid(5, 1);
        nextHex.addTerrain(new Terrain(Terrains.BLDG_ELEV, 1));
        Building building = mock(Building.class);
        when(building.getType()).thenReturn(Building.WALL);
        when(board.getBuildingAt(any(Coords.class))).thenReturn(building);

        skid.updatePosition(game);

        assertTrue(skid.checkTerrainCollision(game));
    }

    @Test
    public void wigeRisesOneLevelOverTerrain() {
        initSkid(2, 3, EntityMovementMode.WIGE, 1);
        
        skid.updatePosition(game);
        
        assertFalse(skid.checkTerrainCollision(game));
        assertEquals(skid.getNextElevation(), 1);
    }

    @Test
    public void wigeCrashesTwoLevelsOverTerrain() {
        initSkid(2, 4, EntityMovementMode.WIGE, 1);
    
        skid.updatePosition(game);
        
        assertTrue(skid.checkTerrainCollision(game));
    }

    @Test
    public void airmechSkidsOverTerrainTwoLevelsHigher() {
        initSkid(2, 4, EntityMovementMode.WIGE, 1);
        when(entity.hasETypeFlag(anyLong()))
            .thenAnswer(inv -> ((Long) inv.getArguments()[0]).longValue() == Entity.ETYPE_LAND_AIR_MECH);
        when(entity.getMovementMode()).thenReturn(EntityMovementMode.WIGE);
        
        skid.updatePosition(game);
        
        assertFalse(skid.checkTerrainCollision(game));
        assertEquals(skid.getNextElevation(), 0);
    }

    @Test
    public void dropshipStopsSkidIfNotDestroyed() {
        initSkid(0, 0);
        Entity dropship = mock(Entity.class);
        
        boolean continueSkid = skid.processDropshipCollision(dropship, game);

        assertFalse(continueSkid);
        assertTrue(childHandlers.stream().anyMatch(h -> h instanceof Server.ChargeDamage));
    }
    
    @Test
    public void skidContinuesIfCollisionDestroysDropship() {
        initSkid(0, 0);
        Entity dropship = mock(Entity.class);
        when(dropship.isDestroyed()).thenReturn(true);
        
        boolean continueSkid = skid.processDropshipCollision(dropship, game);
        
        assertTrue(continueSkid);
    }
    
    @Test
    public void vtolCollisionWithDropship() {
        initSkid(0, 0, EntityMovementMode.VTOL, 5);
        skid.updatePosition(game);
        Entity dropship = mock(Entity.class);
        when(dropship.hasETypeFlag(anyLong()))
            .thenAnswer(inv -> ((Long) inv.getArguments()[0]).longValue() == Entity.ETYPE_DROPSHIP);
        when(dropship.relHeight()).thenReturn(9); // spheroid
        when(dropship.isAirborne()).thenReturn(false);
        List<Entity> entities = Collections.singletonList(dropship);
        IGame mockGame = mock(Game.class);
        when(mockGame.getEntitiesVector(any())).thenReturn(entities);
        
        Entity target = skid.checkDropshipCollision(mockGame);
        
        assertEquals(target, dropship);
    }
    
    @Test
    public void vtolSideslipOverDropship() {
        initSkid(0, 0, EntityMovementMode.VTOL, 10);
        skid.updatePosition(game);
        Entity dropship = mock(Entity.class);
        when(dropship.hasETypeFlag(anyLong()))
            .thenAnswer(inv -> ((Long) inv.getArguments()[0]).longValue() == Entity.ETYPE_DROPSHIP);
        when(dropship.relHeight()).thenReturn(9); // spheroid
        when(dropship.isAirborne()).thenReturn(false);
        List<Entity> entities = Collections.singletonList(dropship);
        IGame mockGame = mock(Game.class);
        when(mockGame.getEntitiesVector(any())).thenReturn(entities);
        
        Entity target = skid.checkDropshipCollision(mockGame);
        
        assertEquals(target, null);
    }
    
    @Test
    public void fallOffCliffEndsSkid() {
        initSkid(5, 0);
        when(entity.getMaxElevationChange()).thenReturn(2);
        skid.updatePosition(game);
        
        assertTrue(skid.fallOffCliff(game));
        assertTrue(childHandlers.stream().anyMatch(h -> h instanceof Server.EntityFallIntoHex));
    }
    
    @Test
    public void doesNotFallOffCliffForChangeWithinLimits() {
        initSkid(2, 0);
        when(entity.getMaxElevationChange()).thenReturn(2);
        skid.updatePosition(game);
        
        assertFalse(skid.fallOffCliff(game));
        assertTrue(skid.getDistRemaining() > 0);
    }
    
    @Test
    public void airborneWigeDoesNotFallOffCliff() {
        initSkid(5, 0, EntityMovementMode.WIGE, 1);
        when(entity.getMaxElevationChange()).thenReturn(1);
        skid.updatePosition(game);
        
        assertFalse(skid.fallOffCliff(game));
        assertTrue(skid.getDistRemaining() > 0);
    }
    
    @Test
    public void infantryAvoidsChargeAutomatically() {
        initSkid(0, 0);
        Entity target = createMockEntity(2);
        when(target.hasETypeFlag(anyLong()))
            .thenAnswer(inv -> ((Long) inv.getArguments()[0]).longValue() == Entity.ETYPE_INFANTRY);
        
        assertTrue(skid.canAvoidSkid(target, game));
    }
    
    @Test
    public void entityAvoidsChargeWithSuccessfulPSR() {
        initSkid(0, 0);
        Entity target = createMockEntity(2);
        PilotingRollData psr = new PilotingRollData(2, PilotingRollData.AUTOMATIC_SUCCESS, "");
        when(target.getBasePilotingRoll()).thenReturn(psr);
        
        assertTrue(skid.canAvoidSkid(target, game));
    }
    
    @Test
    public void entityDoesNotAvoidChargeWithUnsuccessfulPSR() {
        initSkid(0, 0);
        Entity target = createMockEntity(2);
        PilotingRollData psr = new PilotingRollData(2, PilotingRollData.AUTOMATIC_FAIL, "");
        when(target.getBasePilotingRoll()).thenReturn(psr);
        
        assertFalse(skid.canAvoidSkid(target, game));
    }
    
    @Test
    public void largeSupportVeeNeverAvoidsCharge() {
        initSkid(0, 0);
        Entity target = createMockEntity(2);
        when(target.hasETypeFlag(anyLong()))
        .thenAnswer(inv -> ((Long) inv.getArguments()[0]).longValue() == Entity.ETYPE_LARGE_SUPPORT_TANK);
        PilotingRollData psr = new PilotingRollData(2, PilotingRollData.AUTOMATIC_SUCCESS, "");
        when(target.getBasePilotingRoll()).thenReturn(psr);
        
        assertFalse(skid.canAvoidSkid(target, game));
    }
}
