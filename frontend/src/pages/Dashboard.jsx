import { useAuth } from '../context/AuthContext';

const Dashboard = () => {
    const { user, logout } = useAuth();

    return (
        <div className="container mt-5 text-center">
            <h1>Üdvözlünk a Dashboardon, {user?.name}!</h1>
            <p>Ide jönnek majd a leckék és az XP statisztikák.</p>
            <button className="btn btn-danger mt-3" onClick={logout}>Kijelentkezés</button>
        </div>
    );
};
export default Dashboard;