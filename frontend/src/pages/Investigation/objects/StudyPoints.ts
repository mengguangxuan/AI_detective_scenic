import { pointIdSchema } from '../../../objects/PointId';
// Authored bindings for the registered study bundle; never inferred from labels.
export const studyPoints = { drawer: pointIdSchema.parse('point_drawer'), letter: pointIdSchema.parse('point_letter') };
