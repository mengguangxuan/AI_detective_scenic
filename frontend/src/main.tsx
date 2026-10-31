import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import RootApp from './app/RootApp';
import './styles.css';
const root = document.getElementById('root');
if (!root) throw new Error('缺少应用挂载节点');
createRoot(root).render(<StrictMode><RootApp /></StrictMode>);
