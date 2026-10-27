import { sceneNodeIdSchema } from '../../../objects/SceneNodeId';
export const studyNodes = {
  desk: sceneNodeIdSchema.parse('node_desk'), lamp: sceneNodeIdSchema.parse('node_lamp'),
  window: sceneNodeIdSchema.parse('node_window'), bookshelf: sceneNodeIdSchema.parse('node_bookshelf'),
};
export type StudyFocus = 'overview' | 'drawer' | 'letter' | 'desk' | 'lamp' | 'window' | 'bookshelf';
