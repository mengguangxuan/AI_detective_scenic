import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import InvestigationApp from './app/InvestigationApp';
import './styles.css';
const root = document.getElementById('root');
if (!root) throw new Error('缺少应用挂载节点');
createRoot(root).render(<StrictMode><InvestigationApp /></StrictMode>);
