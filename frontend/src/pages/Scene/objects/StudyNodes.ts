import { sceneNodeIdSchema } from '../../../objects/SceneNodeId';
export const studyNodes = {
  planter: sceneNodeIdSchema.parse('node_planter'), planterInterior: sceneNodeIdSchema.parse('node_planter_interior'),
  drawer: sceneNodeIdSchema.parse('node_drawer'),
  telephone: sceneNodeIdSchema.parse('node_telephone'), clock: sceneNodeIdSchema.parse('node_clock'), paper: sceneNodeIdSchema.parse('node_paper'),
  desk: sceneNodeIdSchema.parse('node_desk'), lamp: sceneNodeIdSchema.parse('node_lamp'),
  window: sceneNodeIdSchema.parse('node_window'), bookshelf: sceneNodeIdSchema.parse('node_bookshelf'),
};
