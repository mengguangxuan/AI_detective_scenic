import { sceneNodeIdSchema } from '../../../objects/SceneNodeId';
export const woodlandNodes = {
  trail: sceneNodeIdSchema.parse('node_woodland_trail'), clearing: sceneNodeIdSchema.parse('node_woodland_clearing'),
  leaves: sceneNodeIdSchema.parse('node_woodland_leaves'), fragment: sceneNodeIdSchema.parse('node_woodland_fragment'),
  trees: sceneNodeIdSchema.parse('node_woodland_trees'), stones: sceneNodeIdSchema.parse('node_woodland_stones'),
};
