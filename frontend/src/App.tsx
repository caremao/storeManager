import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AppLayout } from './components/layout/AppLayout'
import { RequireRole } from './components/layout/RequireRole'
import { Home } from './pages/Home'
import { Join } from './pages/Join'
import { NotFound } from './pages/NotFound'
import { Storefront } from './pages/Storefront'
import { GmDashboard } from './pages/gm/GmDashboard'
import { GmInventory } from './pages/gm/GmInventory'
import { GmTable } from './pages/gm/GmTable'
import { PlayerHome } from './pages/player/PlayerHome'
import { ShopCounter } from './pages/player/ShopCounter'
import { ShopList } from './pages/player/ShopList'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/" element={<Home />} />
          <Route path="/join" element={<Join />} />
          <Route path="/join/:token" element={<Join />} />
          <Route path="/vitrina" element={<Storefront />} />
          <Route element={<RequireRole role="GM" />}>
            <Route path="/gm" element={<GmDashboard />} />
            <Route path="/gm/mesa" element={<GmTable />} />
            <Route path="/gm/inventario/:id" element={<GmInventory />} />
          </Route>
          <Route element={<RequireRole role="PLAYER" />}>
            <Route path="/pj" element={<PlayerHome />} />
            <Route path="/pj/tiendas" element={<ShopList />} />
            <Route path="/pj/tiendas/:id" element={<ShopCounter />} />
          </Route>
          <Route path="*" element={<NotFound />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
